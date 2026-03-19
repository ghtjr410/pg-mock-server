package com.example.resilience.retry;

import com.example.resilience.ExampleTestBase;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RetryWithCircuitBreakerTest extends ExampleTestBase {

    /**
     * 잘못된 순서(Retry 바깥 → CB 안쪽)에서 1건의 논리 요청이 CB에 3건으로 집계되는 문제를 검증한다.
     *
     * 흐름:
     *   Retry(바깥) → CB(안쪽) → client
     *   → 요청 1건 실패 시 Retry가 CB를 3번 호출 → CB에 3건 실패 집계
     *   → 2건 논리 요청 x 3회 재시도 = CB에 6건 집계
     *
     * 문제:
     *   slidingWindowSize 10, minimumNumberOfCalls 10일 때
     *   실제 2건만 실패해도 CB에 6건 집계되어 서킷이 의도보다 빨리 열릴 수 있다.
     */
    @Test
    void Retry가_바깥이면_1건_요청이_서킷에_3건으로_집계된다() {
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

    /**
     * 올바른 순서(CB 바깥 → Retry 안쪽)에서 1건의 논리 요청이 CB에 1건으로 집계되는 것을 검증한다.
     *
     * 흐름:
     *   CB(바깥) → Retry(안쪽) → client
     *   → 요청 1건 실패 시 Retry가 내부에서 3번 시도 → CB에는 최종 결과 1건만 집계
     *   → 2건 논리 요청 = CB에 2건 집계
     *
     * 해결:
     *   CB를 바깥에, Retry를 안쪽에 배치하면 Retry의 재시도가 CB 집계를 오염시키지 않는다.
     *   이것이 Resilience4j 공식 권장 순서이다.
     */
    @Test
    void CB가_바깥이면_1건_요청이_서킷에_1건으로_집계된다() {
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
