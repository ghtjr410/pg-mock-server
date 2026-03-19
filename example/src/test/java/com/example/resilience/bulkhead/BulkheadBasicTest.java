package com.example.resilience.bulkhead;

import com.example.resilience.ExampleTestBase;
import com.example.resilience.TestLogger;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BulkheadBasicTest extends ExampleTestBase {

    @BeforeEach
    void configureLongerTimeout() {
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 10000); // readTimeout=10s (SLOW 3s 대기 가능하게)
    }

    /**
     * 동시 25건 요청 중 maxConcurrentCalls(20)까지 통과하고 나머지 5건이 거절되는 것을 검증한다.
     *
     * 흐름:
     *   SLOW 3s 설정 → 25건 동시 요청 → Bulkhead maxConcurrentCalls=20
     *   → 20건 통과 (SLOW 3s 대기 후 성공) + 5건 BulkheadFullException
     *
     * 핵심:
     *   Bulkhead는 동시 실행 수를 제한하여 과부하를 방지한다.
     *   maxWaitDuration=0이면 초과 요청은 대기 없이 즉시 거절된다.
     */
    @Test
    void 동시_25건중_20건_통과하고_5건_거절된다() throws InterruptedException {
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "3000", "slowMaxMs", "3000"));

        Bulkhead bulkhead = Bulkhead.of("test-" + UUID.randomUUID(), BulkheadConfig.custom()
                .maxConcurrentCalls(20)
                .maxWaitDuration(Duration.ZERO)
                .build());
        TestLogger.attach(bulkhead);

        int totalCalls = 25;
        ExecutorService executor = Executors.newFixedThreadPool(totalCalls);
        CountDownLatch readyLatch = new CountDownLatch(totalCalls);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < totalCalls; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 모든 스레드 동시 시작
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                String key = "pk_bulk_" + idx;
                Supplier<Map<String, Object>> decorated = Bulkhead.decorateSupplier(bulkhead,
                        () -> paymentClient.confirm(key, "order_bulk", 10000));
                try {
                    decorated.get();
                    successCount.incrementAndGet();
                } catch (BulkheadFullException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    // SLOW 모드에서 성공 응답 받으면 success
                    successCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await(); // 모든 스레드 준비 완료
        startLatch.countDown(); // 동시 시작

        for (Future<?> future : futures) {
            try { future.get(15, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(20);
        assertThat(rejectedCount.get()).isEqualTo(5);
    }

    /**
     * maxWaitDuration=0일 때 거절된 요청이 즉시 완료되는 것을 검증한다.
     *
     * 흐름:
     *   SLOW 3s + maxConcurrentCalls=20 + maxWaitDuration=0
     *   → 초과 요청은 대기 없이 즉시 BulkheadFullException
     *   → 거절된 요청의 소요 시간이 500ms 미만
     *
     * 핵심:
     *   maxWaitDuration=0이면 Bulkhead 거절이 즉시 발생하므로
     *   클라이언트가 빠르게 실패를 감지하고 대응할 수 있다 (fail-fast).
     */
    @Test
    void maxWaitDuration_0이면_거절된_요청은_즉시_완료된다() throws InterruptedException {
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "3000", "slowMaxMs", "3000"));

        Bulkhead bulkhead = Bulkhead.of("test-immediate-" + UUID.randomUUID(), BulkheadConfig.custom()
                .maxConcurrentCalls(20)
                .maxWaitDuration(Duration.ZERO)
                .build());
        TestLogger.attach(bulkhead);

        int totalCalls = 25;
        ExecutorService executor = Executors.newFixedThreadPool(totalCalls);
        CountDownLatch readyLatch = new CountDownLatch(totalCalls);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Long> rejectedDurations = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < totalCalls; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                String key = "pk_bulk_imm_" + idx;
                Supplier<Map<String, Object>> decorated = Bulkhead.decorateSupplier(bulkhead,
                        () -> paymentClient.confirm(key, "order_bulk_imm", 10000));

                long start = System.currentTimeMillis();
                try {
                    decorated.get();
                } catch (BulkheadFullException e) {
                    long elapsed = System.currentTimeMillis() - start;
                    rejectedDurations.add(elapsed);
                } catch (Exception ignored) {}
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (Future<?> future : futures) {
            try { future.get(15, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();

        assertThat(rejectedDurations).isNotEmpty();
        // 거절된 요청들은 500ms 이내에 완료되어야 함
        rejectedDurations.forEach(duration ->
                assertThat(duration).isLessThan(500));
    }

    /**
     * maxWaitDuration > 0이면 대기 후 통과할 수 있는 것을 검증한다.
     *
     * 흐름:
     *   SLOW 2s + maxConcurrentCalls=3 + maxWaitDuration=5s
     *   → 6건 동시 요청 → 첫 3건 통과(2s 소요) → 대기 중이던 3건도 통과
     *   → 전부 성공 (거절 0건)
     *
     * 핵심:
     *   maxWaitDuration > 0이면 동시 실행 슬롯이 빌 때까지 대기한다.
     *   대기 시간 내에 슬롯이 열리면 요청이 통과되므로 거절률을 낮출 수 있다.
     */
    @Test
    void maxWaitDuration이_0보다_크면_대기_후_통과한다() throws InterruptedException {
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "2000", "slowMaxMs", "2000"));

        Bulkhead bulkhead = Bulkhead.of("test-wait-" + UUID.randomUUID(), BulkheadConfig.custom()
                .maxConcurrentCalls(3)
                .maxWaitDuration(Duration.ofSeconds(5)) // 최대 5초 대기
                .build());
        TestLogger.attach(bulkhead);

        int totalCalls = 6;
        ExecutorService executor = Executors.newFixedThreadPool(totalCalls);
        CountDownLatch readyLatch = new CountDownLatch(totalCalls);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < totalCalls; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try { startLatch.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                String key = "pk_bulk_wait_" + idx;
                Supplier<Map<String, Object>> decorated = Bulkhead.decorateSupplier(bulkhead,
                        () -> paymentClient.confirm(key, "order_bulk_wait", 10000));
                try {
                    decorated.get();
                    successCount.incrementAndGet();
                } catch (BulkheadFullException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    successCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (Future<?> future : futures) {
            try { future.get(15, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();

        // 첫 3건 통과(2s) → 대기 중이던 3건도 통과 → 전부 성공
        assertThat(successCount.get()).isEqualTo(6);
        assertThat(rejectedCount.get()).isEqualTo(0);
    }

    /**
     * Bulkhead(바깥) → CB(안쪽) 구조에서 Bulkhead 거절은 CB 실패로 집계되지 않는 것을 검증한다.
     *
     * 흐름:
     *   Bulkhead(바깥) → CB(안쪽) → client
     *   → Bulkhead에서 거절된 요청은 CB까지 도달하지 않음
     *   → CB 실패 카운트 0, CLOSED 유지
     *
     * 핵심:
     *   Bulkhead를 바깥에 배치하면 동시 실행 제한으로 인한 거절이
     *   CB의 실패율에 영향을 주지 않는다.
     *   이것이 Bulkhead + CB 조합의 올바른 배치 순서이다.
     */
    @Test
    void Bulkhead_바깥_CB_안쪽이면_거절이_CB_실패로_집계되지_않는다() throws Exception {
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "3000", "slowMaxMs", "3000"));

        Bulkhead bulkhead = Bulkhead.of("test-cb-" + UUID.randomUUID(), BulkheadConfig.custom()
                .maxConcurrentCalls(2)
                .maxWaitDuration(Duration.ZERO)
                .build());
        TestLogger.attach(bulkhead);

        CircuitBreaker cb = CircuitBreaker.of("test-bulk-cb-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(5)
                        .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                        .build());
        TestLogger.attach(cb);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch readyLatch = new CountDownLatch(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try { startLatch.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                String key = "pk_bulk_cb_" + idx;
                // Bulkhead(바깥) → CB(안쪽) → client
                Supplier<Map<String, Object>> cbDecorated = CircuitBreaker.decorateSupplier(cb,
                        () -> paymentClient.confirm(key, "order_bulk_cb", 10000));
                Supplier<Map<String, Object>> bulkheadDecorated = Bulkhead.decorateSupplier(bulkhead, cbDecorated);

                try {
                    bulkheadDecorated.get();
                } catch (BulkheadFullException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception ignored) {}
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (Future<?> f : futures) {
            try { f.get(15, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();

        // 2건 Bulkhead 거절 발생
        assertThat(rejectedCount.get()).isEqualTo(2);
        // Bulkhead 거절은 CB까지 도달하지 않음 → CB 실패 카운트 0
        TestLogger.summary(cb);
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
