package com.example.resilience.circuitbreaker;

import com.example.resilience.ExampleTestBase;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTrapTest extends ExampleTestBase {

    @Nested
    @DisplayName("함정1: minimumNumberOfCalls")
    class MinimumNumberOfCalls {

        @Test
        @DisplayName("Before: 기본값(100) → DEAD 10건 실패 → 여전히 CLOSED (평가 안 됨)")
        void before_defaultMinimum_staysClosed() {
            paymentClient.setChaosMode("DEAD");

            CircuitBreaker cb = CircuitBreaker.of("trap1-before-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .slidingWindowSize(100)
                            // minimumNumberOfCalls 기본값 = 100
                            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                            .build());

            for (int i = 0; i < 10; i++) {
                String key = "pk_trap1b_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_trap1", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }

            // 10건밖에 안 했으므로 minimumNumberOfCalls(100) 미달 → 평가 안 됨
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("After: minimumNumberOfCalls=5 → DEAD 5건 실패 → OPEN")
        void after_lowMinimum_opensCircuit() {
            paymentClient.setChaosMode("DEAD");

            CircuitBreaker cb = CircuitBreaker.of("trap1-after-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .minimumNumberOfCalls(5)
                            .slidingWindowSize(10)
                            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                            .build());

            for (int i = 0; i < 5; i++) {
                String key = "pk_trap1a_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_trap1", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("함정2: automaticTransitionFromOpenToHalfOpenEnabled")
    class AutoTransition {

        @Test
        @DisplayName("Before: false(기본) → OPEN 진입 → sleep(waitDuration) → 여전히 OPEN")
        void before_noAutoTransition_staysOpen() throws InterruptedException {
            paymentClient.setChaosMode("DEAD");

            CircuitBreaker cb = CircuitBreaker.of("trap2-before-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .minimumNumberOfCalls(5)
                            .slidingWindowSize(5)
                            .waitDurationInOpenState(Duration.ofSeconds(1))
                            // automaticTransitionFromOpenToHalfOpenEnabled 기본값 = false
                            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                            .build());

            // OPEN 진입
            for (int i = 0; i < 5; i++) {
                String key = "pk_trap2b_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_trap2", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // waitDuration만큼 대기해도 자동 전환 없음
            Thread.sleep(1500);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("After: true → sleep 후 자동 HALF_OPEN 전환")
        void after_autoTransition_becomesHalfOpen() throws InterruptedException {
            paymentClient.setChaosMode("DEAD");

            CircuitBreaker cb = CircuitBreaker.of("trap2-after-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .minimumNumberOfCalls(5)
                            .slidingWindowSize(5)
                            .waitDurationInOpenState(Duration.ofSeconds(1))
                            .automaticTransitionFromOpenToHalfOpenEnabled(true)
                            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                            .build());

            for (int i = 0; i < 5; i++) {
                String key = "pk_trap2a_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_trap2", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            Thread.sleep(1500);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }
    }

    @Nested
    @DisplayName("함정3: ignoreExceptions")
    class IgnoreExceptions {

        @Test
        @DisplayName("Before: 미설정 → reject_company(403) 5건 → 비즈니스 에러가 실패로 카운트 → OPEN")
        void before_noIgnore_businessErrorCountsAsFailure() {
            // reject_company 트리거: orderId에 "reject_company" 포함 시 403
            paymentClient.setChaosMode("NORMAL");

            CircuitBreaker cb = CircuitBreaker.of("trap3-before-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .minimumNumberOfCalls(5)
                            .slidingWindowSize(5)
                            .recordExceptions(HttpServerErrorException.class, HttpClientErrorException.class, ResourceAccessException.class)
                            // ignoreExceptions 미설정 → 4xx도 실패로 카운트
                            .build());

            for (int i = 0; i < 5; i++) {
                String key = "pk_trap3b_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "reject_company", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }

            // 비즈니스 에러(403)가 실패로 집계 → OPEN (오진)
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("After: ignoreExceptions(HttpClientErrorException) → 403 무시 → CLOSED")
        void after_ignoreClientError_staysClosed() {
            paymentClient.setChaosMode("NORMAL");

            CircuitBreaker cb = CircuitBreaker.of("trap3-after-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .minimumNumberOfCalls(5)
                            .slidingWindowSize(5)
                            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                            .ignoreExceptions(HttpClientErrorException.class)
                            .build());

            for (int i = 0; i < 5; i++) {
                String key = "pk_trap3a_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "reject_company", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }

            // 403은 무시됨 → CLOSED
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @Nested
    @DisplayName("함정4: slidingWindowSize 기본값 100 → 감지 느림")
    class SlidingWindowSize {

        @Test
        @DisplayName("Before: 기본값(100) → 100건 채워야 실패율 계산 → 소규모 트래픽에서 서킷 안 열림")
        void before_defaultWindow_slowDetection() {
            paymentClient.setChaosMode("DEAD");

            CircuitBreaker cb = CircuitBreaker.of("trap6-before-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .minimumNumberOfCalls(10)
                            // slidingWindowSize 기본값 = 100
                            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                            .build());

            // 10건 실패 (minimumNumberOfCalls=10 충족) 하지만 window=100 중 10건만 채워짐
            // minimumNumberOfCalls(10)은 충족되므로 실패율 계산은 됨
            // 하지만 기본 slidingWindowSize=100이므로 100건 중 10건만 있는 상태
            for (int i = 0; i < 10; i++) {
                String key = "pk_trap6b_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_trap6", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }

            // 10건 중 10건 실패 → 실패율 100% → 사실 OPEN이 됨
            // 진짜 함정: minimumNumberOfCalls 기본값도 100이면 여기서 CLOSED
            // slidingWindowSize=100 + minimumNumberOfCalls=100 → 100건 채워야 평가
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("Before(진짜 함정): slidingWindowSize=100 + minimumNumberOfCalls 기본값 100 → 10건 실패해도 CLOSED")
        void before_defaultBoth_neverEvaluated() {
            paymentClient.setChaosMode("DEAD");

            CircuitBreaker cb = CircuitBreaker.of("trap6-before2-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            // slidingWindowSize 기본값 = 100, minimumNumberOfCalls 기본값 = 100
                            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                            .build());

            for (int i = 0; i < 10; i++) {
                String key = "pk_trap6b2_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_trap6", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }

            // minimumNumberOfCalls(100) 미달 → 평가 안 됨 → CLOSED
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("After: slidingWindowSize=10 + minimumNumberOfCalls=5 → 빠른 감지")
        void after_smallWindow_fastDetection() {
            paymentClient.setChaosMode("DEAD");

            CircuitBreaker cb = CircuitBreaker.of("trap6-after-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .slidingWindowSize(10)
                            .minimumNumberOfCalls(5)
                            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                            .build());

            for (int i = 0; i < 5; i++) {
                String key = "pk_trap6a_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_trap6", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("함정5: slowCallDurationThreshold")
    class SlowCallThreshold {

        @Test
        @DisplayName("Before: threshold(5s) >= readTimeout(3s) → SLOW 호출이 slow로 안 잡힘")
        void before_thresholdTooHigh_slowNotDetected() {
            // SLOW 모드: 2~2.5s 지연
            paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "2000", "slowMaxMs", "2500"));
            paymentClient.configure(
                    "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                    5000, 3000); // readTimeout=3s

            CircuitBreaker cb = CircuitBreaker.of("trap4-before-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .slowCallDurationThreshold(Duration.ofSeconds(5)) // threshold(5s) > readTimeout(3s)
                            .slowCallRateThreshold(50)
                            .minimumNumberOfCalls(5)
                            .slidingWindowSize(5)
                            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                            .build());

            for (int i = 0; i < 5; i++) {
                String key = "pk_trap4b_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_trap4", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }

            // 2~2.5s < 5s(threshold) → slow로 안 잡힘 → CLOSED
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("After: threshold(1s) < readTimeout(5s) → SLOW(2~2.5s) > 1s → slow 감지 → OPEN")
        void after_thresholdLow_slowDetected() {
            paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "2000", "slowMaxMs", "2500"));
            paymentClient.configure(
                    "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                    5000, 5000); // readTimeout=5s

            CircuitBreaker cb = CircuitBreaker.of("trap4-after-" + UUID.randomUUID(),
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(100) // 일반 실패로는 안 열리게
                            .slowCallDurationThreshold(Duration.ofSeconds(1)) // threshold(1s) < SLOW(2~2.5s)
                            .slowCallRateThreshold(50)
                            .minimumNumberOfCalls(5)
                            .slidingWindowSize(5)
                            .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                            .build());

            for (int i = 0; i < 5; i++) {
                String key = "pk_trap4a_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_trap4", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }

            // 2~2.5s > 1s(threshold) → slow 감지 → OPEN
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("함정6: maxWaitDurationInHalfOpenState 기본값 0 → HALF-OPEN 갇힘")
    class MaxWaitDurationInHalfOpen {

        private CircuitBreaker openCircuit(CircuitBreakerConfig config) {
            CircuitBreaker cb = CircuitBreaker.of("trap7-" + UUID.randomUUID(), config);
            paymentClient.setChaosMode("DEAD");
            for (int i = 0; i < 5; i++) {
                String key = "pk_trap7_" + i;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_trap7", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            return cb;
        }

        @Test
        @DisplayName("Before: 미설정(기본값 0=무한) → HALF-OPEN에서 SLOW 요청 대기 중 갇힘")
        void before_defaultMaxWait_stuckInHalfOpen() throws Exception {
            CircuitBreaker cb = openCircuit(CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .minimumNumberOfCalls(5)
                    .slidingWindowSize(5)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    // maxWaitDurationInHalfOpenState 기본값 = 0 (무한)
                    .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                    .build());

            paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "5000", "slowMaxMs", "5000"));
            paymentClient.configure(
                    "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                    5000, 10000);

            // 수동 HALF_OPEN 전환
            cb.transitionToHalfOpenState();
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            // 1건 SLOW 호출 시작
            var executor = java.util.concurrent.Executors.newFixedThreadPool(1);
            executor.submit(() -> {
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm("pk_trap7_slow", "order_trap7_slow", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            });

            // 3초 후에도 HALF_OPEN에 갇혀 있음 (무한 대기)
            Thread.sleep(3000);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            executor.shutdownNow();
        }

        @Test
        @DisplayName("After: maxWaitDuration=2s → 시간 초과 시 OPEN 복귀 (무한 대기 방지)")
        void after_maxWaitSet_forcesOpenAfterTimeout() throws Exception {
            // waitDurationInOpenState를 길게 → maxWait→OPEN 후 재전환 방지
            CircuitBreaker cb = openCircuit(CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .minimumNumberOfCalls(5)
                    .slidingWindowSize(5)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .maxWaitDurationInHalfOpenState(Duration.ofSeconds(2))
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                    .build());

            paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "10000", "slowMaxMs", "10000"));
            paymentClient.configure(
                    "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                    5000, 15000);

            // 수동으로 HALF_OPEN 전환 (30s 대기 불필요)
            cb.transitionToHalfOpenState();
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            var executor = java.util.concurrent.Executors.newFixedThreadPool(3);
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                executor.submit(() -> {
                    Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                            () -> paymentClient.confirm("pk_trap7_mw_" + idx, "order_trap7_mw", 10000));
                    try { decorated.get(); } catch (Exception ignored) {}
                });
            }

            // maxWaitDuration(2s) 후 강제 OPEN 복귀
            Thread.sleep(3000);
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            executor.shutdownNow();
        }
    }
}
