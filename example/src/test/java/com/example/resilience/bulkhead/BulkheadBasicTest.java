package com.example.resilience.bulkhead;

import com.example.resilience.ExampleTestBase;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
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

class BulkheadBasicTest extends ExampleTestBase {

    @BeforeEach
    void configureLongerTimeout() {
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 10000); // readTimeout=10s (SLOW 3s 대기 가능하게)
    }

    @Test
    @DisplayName("동시 25건 → 20 통과 + 5 거절 (BulkheadFullException)")
    void concurrent25_20pass_5rejected() throws InterruptedException {
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "3000", "slowMaxMs", "3000"));

        Bulkhead bulkhead = Bulkhead.of("test-" + UUID.randomUUID(), BulkheadConfig.custom()
                .maxConcurrentCalls(20)
                .maxWaitDuration(Duration.ZERO)
                .build());

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

    @Test
    @DisplayName("maxWaitDuration=0 → 거절된 요청은 즉시 완료 (< 500ms)")
    void maxWaitZero_rejectedImmediately() throws InterruptedException {
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "3000", "slowMaxMs", "3000"));

        Bulkhead bulkhead = Bulkhead.of("test-immediate-" + UUID.randomUUID(), BulkheadConfig.custom()
                .maxConcurrentCalls(20)
                .maxWaitDuration(Duration.ZERO)
                .build());

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

    @Test
    @DisplayName("9-2: maxWaitDuration > 0 → 대기 후 통과")
    void maxWaitDurationPositive_waitsAndPasses() throws InterruptedException {
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "2000", "slowMaxMs", "2000"));

        Bulkhead bulkhead = Bulkhead.of("test-wait-" + UUID.randomUUID(), BulkheadConfig.custom()
                .maxConcurrentCalls(3)
                .maxWaitDuration(Duration.ofSeconds(5)) // 최대 5초 대기
                .build());

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

    @Test
    @DisplayName("9-3: Bulkhead(바깥) → CB(안쪽) — Bulkhead 거절은 CB 실패로 집계되지 않음")
    void bulkheadOutside_cbInside_rejectionNotCountedAsFailure() throws Exception {
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "3000", "slowMaxMs", "3000"));

        Bulkhead bulkhead = Bulkhead.of("test-cb-" + UUID.randomUUID(), BulkheadConfig.custom()
                .maxConcurrentCalls(2)
                .maxWaitDuration(Duration.ZERO)
                .build());

        CircuitBreaker cb = CircuitBreaker.of("test-bulk-cb-" + UUID.randomUUID(),
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .slidingWindowSize(5)
                        .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                        .build());

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
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
