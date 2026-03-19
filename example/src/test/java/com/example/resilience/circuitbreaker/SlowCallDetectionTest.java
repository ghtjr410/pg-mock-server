package com.example.resilience.circuitbreaker;

import com.example.resilience.ExampleTestBase;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class SlowCallDetectionTest extends ExampleTestBase {

    @Test
    @DisplayName("2-1: slowCall 기본 동작 — SLOW 전부 → slowCallRate 100% → OPEN")
    void slowCall_allSlow_opensCircuit() {
        // SLOW 2~2.5s, threshold 1s → 전부 slowCall
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "2000", "slowMaxMs", "2500"));
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 5000); // readTimeout=5s (SLOW보다 넉넉하게)

        CircuitBreaker cb = CircuitBreaker.of("slow-basic-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(100)  // 일반 실패로는 안 열리게
                        .slowCallDurationThreshold(Duration.ofSeconds(1))
                        .slowCallRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(5)
                        .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                        .build());

        for (int i = 0; i < 5; i++) {
            String key = "pk_slow_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_slow", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }

        // slowCallRate 100% > 50% → OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.getMetrics().getSlowCallRate()).isGreaterThanOrEqualTo(50.0f);
    }

    @Test
    @DisplayName("2-2: slowCall은 요청을 끊지 않는다 — 느린 성공도 장애의 전조")
    void slowCall_doesNotCutRequest_slowSuccessIsWarning() {
        // SLOW 2s, threshold 1s, readTimeout 5s
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "2000", "slowMaxMs", "2000"));
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 5000);

        CircuitBreaker cb = CircuitBreaker.of("slow-success-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(100)
                        .slowCallDurationThreshold(Duration.ofSeconds(1))
                        .slowCallRateThreshold(80)
                        .minimumNumberOfCalls(3)
                        .slidingWindowSize(3)
                        .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                        .build());

        // 3건 모두 느리지만 성공
        for (int i = 0; i < 3; i++) {
            String key = "pk_slow_ok_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_slow_ok", 10000));
            Map<String, Object> result = decorated.get();
            // 응답을 정상적으로 받음 (끊기지 않음)
            assertThat(result).isNotNull();
        }

        // 사용자에게는 성공이지만 서킷에는 slowCall로 집계됨
        assertThat(cb.getMetrics().getNumberOfSlowSuccessfulCalls()).isEqualTo(3);
        assertThat(cb.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(3);
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
        // slowCallRate 100% > 80% → OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("2-3: slowCall + failure 복합 — 둘 중 하나라도 threshold 넘으면 OPEN")
    void slowCall_plusFailure_composite() {
        // SLOW 2s → 모든 호출이 느림. 에러 트리거로 일부 실패 추가.
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "2000", "slowMaxMs", "2000"));
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 5000);

        CircuitBreaker cb = CircuitBreaker.of("slow-composite-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(80)    // 실패율로는 안 열리게 (높은 threshold)
                        .slowCallDurationThreshold(Duration.ofSeconds(1))
                        .slowCallRateThreshold(50)    // slowCallRate로 열림
                        .minimumNumberOfCalls(4)
                        .slidingWindowSize(4)
                        .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                        .build());

        // 2건 정상 (느린 성공) + 2건 에러 트리거 (느린 실패)
        for (int i = 0; i < 2; i++) {
            String key = "pk_sc_ok_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_sc_ok", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        for (int i = 0; i < 2; i++) {
            String key = "pk_sc_err_" + i;
            // system_error 트리거 → 500 (느린 실패)
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "system_error", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }

        // failureRate: 50% (2/4) < 80% → 이것만으로는 안 열림
        // slowCallRate: 100% (4/4) > 50% → 이걸로 열림
        assertThat(cb.getMetrics().getNumberOfSlowCalls()).isEqualTo(4);
        assertThat(cb.getMetrics().getNumberOfSlowFailedCalls()).isEqualTo(2);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
