package com.example.resilience.circuitbreaker;

import com.example.resilience.ExampleTestBase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HalfOpenBehaviorTest extends ExampleTestBase {

    private CircuitBreaker openCircuit(CircuitBreakerConfig config) {
        CircuitBreaker cb = CircuitBreaker.of("ho-" + UUID.randomUUID(), config);
        paymentClient.setChaosMode("DEAD");
        for (int i = 0; i < 5; i++) {
            String key = "pk_ho_" + i;
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm(key, "order_ho", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        return cb;
    }

    @Test
    @DisplayName("3-1: permittedNumberOfCalls 초과 요청은 거절")
    void halfOpen_exceedPermittedCalls_rejected() throws Exception {
        CircuitBreaker cb = openCircuit(CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(5)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());

        // SLOW 모드로 전환 → permitted 호출이 오래 걸리게
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "5000", "slowMaxMs", "5000"));
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 10000); // readTimeout=10s

        // HALF_OPEN 전환 대기
        Thread.sleep(1500);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // 3건을 병렬로 시작 (SLOW 5s → 슬롯 점유)
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch started = new CountDownLatch(3);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                started.countDown();
                String key = "pk_ho_perm_" + idx;
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_ho_perm", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            }));
        }

        started.await(3, TimeUnit.SECONDS);
        Thread.sleep(500); // permitted 호출이 진행 중임을 보장

        // 4번째 요청 → CallNotPermittedException
        Supplier<Map<String, Object>> fourth = CircuitBreaker.decorateSupplier(cb,
                () -> paymentClient.confirm("pk_ho_4th", "order_ho_4th", 10000));
        assertThatThrownBy(fourth::get).isInstanceOf(CallNotPermittedException.class);

        for (Future<?> f : futures) { try { f.get(15, TimeUnit.SECONDS); } catch (Exception ignored) {} }
        executor.shutdown();
    }

    @Test
    @DisplayName("3-2: maxWaitDurationInHalfOpenState 기본값 0 → 무한 대기")
    void halfOpen_maxWaitDefault_waitsForever() throws Exception {
        CircuitBreaker cb = openCircuit(CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(5)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // maxWaitDurationInHalfOpenState 기본값 = 0 (무한)
                .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());

        // SLOW 5s → permitted 호출이 오래 걸림
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "5000", "slowMaxMs", "5000"));
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 10000);

        Thread.sleep(1500);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // 1건 SLOW 호출 시작 (5s 소요)
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Future<?> slowCall = executor.submit(() -> {
            Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                    () -> paymentClient.confirm("pk_ho_slow", "order_ho_slow", 10000));
            try { decorated.get(); } catch (Exception ignored) {}
        });

        Thread.sleep(500); // 호출이 진행 중

        // 3초 후에도 여전히 HALF_OPEN (maxWait=0 → 무한 대기)
        Thread.sleep(2500);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        try { slowCall.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
        executor.shutdown();
    }

    @Test
    @DisplayName("3-3: maxWaitDurationInHalfOpenState 설정 → 시간 초과 시 OPEN 복귀")
    void halfOpen_maxWaitSet_forcesOpenAfterTimeout() throws Exception {
        // waitDurationInOpenState를 길게 설정: maxWait→OPEN 후 재전환 방지
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

        // SLOW 10s → permitted 호출이 매우 오래 걸림
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "10000", "slowMaxMs", "10000"));
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 15000);

        // 수동으로 HALF_OPEN 전환 (30s 대기 불필요)
        cb.transitionToHalfOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // 3건 SLOW 호출 시작 (10s 소요 → maxWait 2s 초과)
        ExecutorService executor = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            executor.submit(() -> {
                Supplier<Map<String, Object>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm("pk_ho_mw_" + idx, "order_ho_mw", 10000));
                try { decorated.get(); } catch (Exception ignored) {}
            });
        }

        // maxWaitDuration(2s) 후 강제 OPEN 복귀
        // waitDuration=30s이므로 다시 HALF_OPEN 되지 않음
        Thread.sleep(3000);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        executor.shutdownNow();
    }
}
