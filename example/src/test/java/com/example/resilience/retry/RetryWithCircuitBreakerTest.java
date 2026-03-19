package com.example.resilience.retry;

import com.example.resilience.ExampleTestBase;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RetryWithCircuitBreakerTest extends ExampleTestBase {

    @Test
    @DisplayName("잘못된 순서: Retry(바깥) → CB(안쪽) → 1 논리 요청 = CB에 3건 집계")
    void wrongOrder_retryOutside_cbCountsEachRetry() {
        paymentClient.setChaosMode("DEAD");

        CircuitBreaker cb = CircuitBreaker.of("wrong-order-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(10)
                        .slidingWindowSize(10)
                        .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                        .build());

        Retry retry = Retry.of("wrong-order-retry-" + UUID.randomUUID(), RetryConfig.custom()
                .maxAttempts(3)
                .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());

        // 잘못된 순서: Retry → CB → client
        // Retry가 CB를 3번 호출 → CB에 3건 집계
        for (int i = 0; i < 2; i++) {
            String key = "pk_wrong_" + i;
            Supplier<Map<String, Object>> cbDecorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_wrong", 10000));
            Supplier<Map<String, Object>> retryDecorated = Retry.decorateSupplier(retry, cbDecorated);
            try { retryDecorated.get(); } catch (Exception ignored) {}
        }

        // 2 논리 요청 × 3 재시도 = CB에 6건 집계
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(6);
    }

    @Test
    @DisplayName("올바른 순서: CB(바깥) → Retry(안쪽) → 1 논리 요청 = CB에 1건 집계")
    void correctOrder_cbOutside_retryInternal() {
        paymentClient.setChaosMode("DEAD");

        CircuitBreaker cb = CircuitBreaker.of("correct-order-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(10)
                        .slidingWindowSize(10)
                        .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                        .build());

        Retry retry = Retry.of("correct-order-retry-" + UUID.randomUUID(), RetryConfig.custom()
                .maxAttempts(3)
                .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());

        // 올바른 순서: CB → Retry → client
        // Retry가 내부에서 소화 → CB에 1건만 집계
        for (int i = 0; i < 2; i++) {
            String key = "pk_correct_" + i;
            Supplier<Map<String, Object>> retryDecorated = Retry.decorateSupplier(retry,
                    () -> paymentClient.confirm(key, "order_correct", 10000));
            Supplier<Map<String, Object>> cbDecorated = CircuitBreaker.decorateSupplier(cb, retryDecorated);
            try { cbDecorated.get(); } catch (Exception ignored) {}
        }

        // 2 논리 요청 = CB에 2건 집계
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }
}
