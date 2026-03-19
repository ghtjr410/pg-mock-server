package com.example.resilience.circuitbreaker;

import com.example.resilience.ExampleTestBase;
import com.example.resilience.TestLogger;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CircuitBreakerBasicTest extends ExampleTestBase {

    private CircuitBreaker createCircuitBreaker() {
        return CircuitBreaker.of("test-" + UUID.randomUUID(), CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(10)
                .slidingWindowSize(10)
                .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());
    }

    /**
     * DEAD 모드에서 모든 요청이 실패하면 서킷이 OPEN으로 전환되는지 검증한다.
     *
     * 흐름:
     *   DEAD 모드 설정 → 10건 요청 전부 실패 → 실패율 100%
     *   → failureRateThreshold(50%) 초과 → OPEN 전환
     *   → 11번째 요청은 서킷이 열려있으므로 CallNotPermittedException 발생
     */
    @Test
    void DEAD_모드에서_모든_요청_실패시_서킷이_OPEN으로_전환된다() {
        paymentClient.setChaosMode("DEAD");
        CircuitBreaker cb = createCircuitBreaker();
        TestLogger.attach(cb);

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

        TestLogger.summary(cb);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 11번째는 CallNotPermittedException
        Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                () -> paymentClient.confirm("pk_dead_11", "order_dead", 10000));
        assertThatThrownBy(decorated::get).isInstanceOf(CallNotPermittedException.class);
    }

    /**
     * 부분 장애율 30%일 때 서킷이 CLOSED를 유지하는지 검증한다.
     *
     * 흐름:
     *   PARTIAL_FAILURE 30% 설정 → 100건 요청 → 약 30% 실패
     *   → failureRateThreshold(50%) 미만 → CLOSED 유지
     *
     * 핵심:
     *   실패율이 threshold 미만이면 서킷은 열리지 않는다.
     */
    @Test
    void PARTIAL_FAILURE_30퍼센트이면_실패율_50퍼센트_미만으로_CLOSED_유지() {
        paymentClient.setChaosMode("PARTIAL_FAILURE", Map.of("partialFailureRate", "30"));
        CircuitBreaker cb = createCircuitBreaker();
        TestLogger.attach(cb);

        for (int i = 0; i < 100; i++) {
            String key = "pk_pf30_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_pf30", 10000));
            try {
                decorated.get();
            } catch (HttpServerErrorException | HttpClientErrorException | ResourceAccessException ignored) {
            }
        }

        TestLogger.summary(cb);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    /**
     * 부분 장애율 90%일 때 서킷이 OPEN으로 전환되는지 검증한다.
     *
     * 흐름:
     *   PARTIAL_FAILURE 90% 설정 → 100건 요청 → 약 90% 실패
     *   → failureRateThreshold(50%) 초과 → OPEN 전환
     *
     * 주의:
     *   PARTIAL_FAILURE는 4xx/5xx를 혼합 반환하므로 HttpClientErrorException도
     *   recordExceptions에 포함해야 정확한 장애율을 집계할 수 있다.
     */
    @Test
    void PARTIAL_FAILURE_90퍼센트이면_실패율_50퍼센트_초과로_OPEN_전환() {
        paymentClient.setChaosMode("PARTIAL_FAILURE", Map.of("partialFailureRate", "90"));

        // PARTIAL_FAILURE는 4xx/5xx 혼합 반환 → 둘 다 recordExceptions에 포함해야 정확한 장애율 집계
        CircuitBreaker cb = CircuitBreaker.of("pf70-" + UUID.randomUUID(), CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(10)
                .slidingWindowSize(10)
                .recordExceptions(HttpServerErrorException.class, HttpClientErrorException.class, ResourceAccessException.class)
                .build());
        TestLogger.attach(cb);

        for (int i = 0; i < 100; i++) {
            String key = "pk_pf70_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_pf70", 10000));
            try {
                decorated.get();
            } catch (HttpServerErrorException | HttpClientErrorException | ResourceAccessException | CallNotPermittedException ignored) {
            }
        }

        TestLogger.summary(cb);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
