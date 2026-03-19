package com.example.resilience.circuitbreaker;

import com.example.resilience.ExampleTestBase;
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

class ExceptionHandlingTest extends ExampleTestBase {

    @Test
    @DisplayName("4-1: 기본 동작 — 모든 예외가 실패로 집계 → 비즈니스 에러도 서킷 오진")
    void defaultBehavior_allExceptionsAreFailures() {
        paymentClient.setChaosMode("NORMAL");

        // recordExceptions/ignoreExceptions 미설정 → 모든 예외가 실패
        CircuitBreaker cb = CircuitBreaker.of("exc-default-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(5)
                        .build());

        // reject_company → 403 비즈니스 에러
        for (int i = 0; i < 5; i++) {
            String key = "pk_exc_def_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "reject_company", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }

        // 비즈니스 에러(403)가 실패로 집계 → OPEN (오진!)
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("4-2: ignoreExceptions — 특정 예외 무시")
    void ignoreExceptions_specificExceptionsIgnored() {
        paymentClient.setChaosMode("NORMAL");

        CircuitBreaker cb = CircuitBreaker.of("exc-ignore-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(10)
                        .ignoreExceptions(HttpClientErrorException.class)
                        .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                        .build());

        // 403 에러 10건 → 서킷 CLOSED (ignoreExceptions로 무시됨)
        for (int i = 0; i < 10; i++) {
            String key = "pk_exc_ign_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "reject_company", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        // ignore된 호출은 실패 카운트에 들어가지 않음
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);

        // 500 에러 5건 → 서킷 OPEN (recordExceptions에 포함)
        for (int i = 0; i < 5; i++) {
            String key = "pk_exc_ign5_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "system_error", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isGreaterThan(0);
    }

    @Test
    @DisplayName("4-3: recordExceptions — 특정 예외만 실패로 기록")
    void recordExceptions_onlySpecificExceptionsRecorded() {
        paymentClient.setChaosMode("NORMAL");

        // HttpServerErrorException만 recordExceptions에 포함
        CircuitBreaker cb = CircuitBreaker.of("exc-record-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(5)
                        .recordExceptions(HttpServerErrorException.class)
                        .build());

        // 403 → recordExceptions에 없으므로 성공으로 집계
        for (int i = 0; i < 3; i++) {
            String key = "pk_exc_rec4_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "reject_company", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
        assertThat(cb.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(3);

        // 500 → recordExceptions에 포함 → 실패로 집계
        for (int i = 0; i < 2; i++) {
            String key = "pk_exc_rec5_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "system_error", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("4-4: recordFailurePredicate — 커스텀 실패 판단 (5xx만 실패)")
    void recordFailurePredicate_customFailureLogic() {
        paymentClient.setChaosMode("NORMAL");

        CircuitBreaker cb = CircuitBreaker.of("exc-pred-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(5)
                        // Predicate: 5xx와 timeout만 실패로 판단
                        .recordException(e ->
                                e instanceof HttpServerErrorException ||
                                e instanceof ResourceAccessException)
                        .build());

        // 403 → Predicate false → 성공으로 집계
        for (int i = 0; i < 3; i++) {
            String key = "pk_exc_pred4_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "reject_company", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);

        // 500 → Predicate true → 실패로 집계
        for (int i = 0; i < 2; i++) {
            String key = "pk_exc_pred5_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "system_error", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("4-5: ignoreExceptions vs recordExceptions 우선순위 — ignore가 이긴다")
    void ignoreVsRecord_ignoreWins() {
        paymentClient.setChaosMode("NORMAL");

        CircuitBreaker cb = CircuitBreaker.of("exc-priority-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(5)
                        // RuntimeException은 HttpClientErrorException의 부모
                        .recordExceptions(RuntimeException.class)
                        // HttpClientErrorException을 ignore → RuntimeException에 해당하더라도 무시
                        .ignoreExceptions(HttpClientErrorException.class)
                        .build());

        // reject_company → HttpClientErrorException(403)
        // RuntimeException 하위지만 ignoreExceptions가 우선 → 무시됨
        for (int i = 0; i < 5; i++) {
            String key = "pk_exc_pri_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "reject_company", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }

        // ignoreExceptions가 우선 → 실패로 집계되지 않음 → CLOSED
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
    }
}
