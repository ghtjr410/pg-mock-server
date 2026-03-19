package com.example.resilience.circuitbreaker;

import com.example.resilience.ExampleTestBase;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CircuitBreakerRecoveryTest extends ExampleTestBase {

    /**
     * HALF_OPEN 상태에서 성공 응답을 받으면 CLOSED로 복구되는지 검증한다.
     *
     * 흐름:
     *   1. DEAD 모드 → 5건 실패 → OPEN 전환
     *   2. NORMAL 모드로 전환 + waitDuration(1s) 대기 → HALF_OPEN 자동 전환
     *   3. HALF_OPEN에서 permittedNumberOfCalls(3)건 성공 → CLOSED 복구
     *
     * 핵심:
     *   서킷 복구의 핵심 경로. 장애가 해소되면 HALF_OPEN을 거쳐 정상으로 돌아온다.
     */
    @Test
    void HALF_OPEN에서_성공하면_CLOSED로_복구된다() throws InterruptedException {
        CircuitBreaker cb = CircuitBreaker.of("recovery-success-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(5)
                        .waitDurationInOpenState(Duration.ofSeconds(1))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                        .build());

        // DEAD → OPEN
        paymentClient.setChaosMode("DEAD");
        for (int i = 0; i < 5; i++) {
            String key = "pk_rec_s_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_rec", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // NORMAL로 전환 + 대기 → HALF_OPEN
        paymentClient.setChaosMode("NORMAL");
        Thread.sleep(1500);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // HALF_OPEN에서 3건 성공 → CLOSED
        for (int i = 0; i < 3; i++) {
            String key = "pk_rec_s_ok_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_rec_ok", 10000));
            decorated.get();
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    /**
     * HALF_OPEN 상태에서 다시 실패하면 OPEN으로 재진입하는지 검증한다.
     *
     * 흐름:
     *   1. DEAD 모드 → 5건 실패 → OPEN 전환
     *   2. waitDuration(1s) 대기 → HALF_OPEN 자동 전환 (DEAD 유지)
     *   3. HALF_OPEN에서 3건 실패 → OPEN 재진입
     *
     * 핵심:
     *   장애가 계속되면 HALF_OPEN에서 다시 OPEN으로 돌아간다.
     *   서킷이 장애 서버에 트래픽을 보내지 않도록 보호하는 메커니즘이다.
     */
    @Test
    void HALF_OPEN에서_실패하면_OPEN으로_재진입한다() throws InterruptedException {
        CircuitBreaker cb = CircuitBreaker.of("recovery-fail-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(5)
                        .waitDurationInOpenState(Duration.ofSeconds(1))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                        .build());

        // DEAD → OPEN
        paymentClient.setChaosMode("DEAD");
        for (int i = 0; i < 5; i++) {
            String key = "pk_rec_f_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_rec", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 대기 → HALF_OPEN (DEAD 유지)
        Thread.sleep(1500);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // HALF_OPEN에서 실패 → OPEN 재진입
        for (int i = 0; i < 3; i++) {
            String key = "pk_rec_f_fail_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_rec_fail", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
