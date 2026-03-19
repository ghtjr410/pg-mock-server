package com.example.resilience.circuitbreaker;

import com.example.resilience.ExampleTestBase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerBasicTest extends ExampleTestBase {

    private CircuitBreaker createCircuitBreaker() {
        return CircuitBreaker.of("test-" + UUID.randomUUID(), CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(10)
                .slidingWindowSize(10)
                .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());
    }

    @Test
    @DisplayName("DEAD → 모든 요청 실패 → OPEN 전환")
    void dead_allFail_opensCircuit() {
        paymentClient.setChaosMode("DEAD");
        CircuitBreaker cb = createCircuitBreaker();

        // 10건 실패 → 실패율 100% → OPEN
        for (int i = 0; i < 10; i++) {
            String key = "pk_dead_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_dead", 10000));
            try {
                decorated.get();
            } catch (HttpServerErrorException | ResourceAccessException ignored) {
            }
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 11번째는 CallNotPermittedException
        Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                () -> paymentClient.confirm("pk_dead_11", "order_dead", 10000));
        assertThatThrownBy(decorated::get).isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("PARTIAL_FAILURE 30% → 실패율 50% 미만 → CLOSED 유지")
    void partialFailure30_staysClosed() {
        paymentClient.setChaosMode("PARTIAL_FAILURE", Map.of("partialFailureRate", "30"));
        CircuitBreaker cb = createCircuitBreaker();

        for (int i = 0; i < 100; i++) {
            String key = "pk_pf30_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_pf30", 10000));
            try {
                decorated.get();
            } catch (HttpServerErrorException | HttpClientErrorException | ResourceAccessException ignored) {
            }
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("PARTIAL_FAILURE 70% → 실패율 50% 초과 → OPEN 전환")
    void partialFailure70_opensCircuit() {
        paymentClient.setChaosMode("PARTIAL_FAILURE", Map.of("partialFailureRate", "90"));

        // PARTIAL_FAILURE는 4xx/5xx 혼합 반환 → 둘 다 recordExceptions에 포함해야 정확한 장애율 집계
        CircuitBreaker cb = CircuitBreaker.of("pf70-" + UUID.randomUUID(), CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(10)
                .slidingWindowSize(10)
                .recordExceptions(HttpServerErrorException.class, HttpClientErrorException.class, ResourceAccessException.class)
                .build());

        for (int i = 0; i < 100; i++) {
            String key = "pk_pf70_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_pf70", 10000));
            try {
                decorated.get();
            } catch (HttpServerErrorException | HttpClientErrorException | ResourceAccessException | CallNotPermittedException ignored) {
            }
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
