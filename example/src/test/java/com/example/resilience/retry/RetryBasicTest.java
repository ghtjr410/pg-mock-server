package com.example.resilience.retry;

import com.example.resilience.ExampleTestBase;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryBasicTest extends ExampleTestBase {

    @Test
    @DisplayName("6-1: 1회 실패 후 재시도 성공 — Retry 이벤트로 DEAD→NORMAL 전환")
    void firstFail_thenSuccess_viaRetryEvent() {
        paymentClient.setChaosMode("DEAD"); // 첫 시도 실패

        Retry retry = Retry.of("retry-recover-" + UUID.randomUUID(), RetryConfig.custom()
                .maxAttempts(3)
                .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());

        // 재시도 시 NORMAL로 전환 → 두 번째 시도는 성공
        retry.getEventPublisher().onRetry(event ->
                paymentClient.setChaosMode("NORMAL"));

        Supplier<Map<String, Object>> decorated = Retry.decorateSupplier(retry,
                () -> paymentClient.confirm("pk_retry_recover", "order_retry_recover", 10000));

        Map<String, Object> result = decorated.get(); // 2번째 시도에서 성공
        assertThat(result).isNotNull();
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("6-2: DEAD → 3회 전부 실패 → 최종 HttpServerErrorException")
    void dead_allRetriesFail_throwsException() {
        paymentClient.setChaosMode("DEAD");

        Retry retry = Retry.of("retry-dead-" + UUID.randomUUID(), RetryConfig.custom()
                .maxAttempts(3)
                .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());

        Supplier<Map<String, Object>> decorated = Retry.decorateSupplier(retry,
                () -> paymentClient.confirm("pk_retry_dead", "order_retry", 10000));

        assertThatThrownBy(decorated::get).isInstanceOf(HttpServerErrorException.class);

        // 재시도 포함 실패 1건 (내부적으로 3회 시도)
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("TIMEOUT → ResourceAccessException으로 재시도")
    void timeout_retriesOnResourceAccessException() {
        paymentClient.setChaosMode("TIMEOUT");
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 2000); // readTimeout=2s

        Retry retry = Retry.of("retry-timeout-" + UUID.randomUUID(), RetryConfig.custom()
                .maxAttempts(2)
                .retryExceptions(ResourceAccessException.class)
                .build());

        Supplier<Map<String, Object>> decorated = Retry.decorateSupplier(retry,
                () -> paymentClient.confirm("pk_retry_to", "order_retry_to", 10000));

        assertThatThrownBy(decorated::get).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("DEAD + retryExceptions에 HttpServerErrorException 없으면 → 재시도 없이 즉시 실패")
    void dead_notInRetryExceptions_noRetry() {
        paymentClient.setChaosMode("DEAD");

        Retry retry = Retry.of("retry-no-match-" + UUID.randomUUID(), RetryConfig.custom()
                .maxAttempts(3)
                .retryExceptions(ResourceAccessException.class) // HttpServerErrorException 없음
                .build());

        Supplier<Map<String, Object>> decorated = Retry.decorateSupplier(retry,
                () -> paymentClient.confirm("pk_retry_no", "order_retry_no", 10000));

        assertThatThrownBy(decorated::get).isInstanceOf(HttpServerErrorException.class);

        // 재시도 없이 즉시 실패
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("비즈니스 에러(403) → 재시도 안 함")
    void businessError_noRetry() {
        paymentClient.setChaosMode("NORMAL");

        Retry retry = Retry.of("retry-biz-" + UUID.randomUUID(), RetryConfig.custom()
                .maxAttempts(3)
                .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());

        Supplier<Map<String, Object>> decorated = Retry.decorateSupplier(retry,
                () -> paymentClient.confirm("pk_retry_biz", "reject_company", 10000));

        assertThatThrownBy(decorated::get).isInstanceOf(HttpClientErrorException.class);

        // 재시도 없이 즉시 실패
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("6-3: waitDuration 설정 → 재시도 간 대기 시간 확인")
    void waitDuration_addsDelayBetweenRetries() {
        paymentClient.setChaosMode("DEAD");

        Retry retry = Retry.of("retry-wait-" + UUID.randomUUID(), RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1)) // 재시도 간 1초 대기
                .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());

        Supplier<Map<String, Object>> decorated = Retry.decorateSupplier(retry,
                () -> paymentClient.confirm("pk_retry_wait", "order_retry_wait", 10000));

        long start = System.currentTimeMillis();
        assertThatThrownBy(decorated::get).isInstanceOf(HttpServerErrorException.class);
        long elapsed = System.currentTimeMillis() - start;

        // 3회 시도, 2번 대기(각 1초) → 최소 2초 소요
        assertThat(elapsed).isGreaterThanOrEqualTo(2000L);
    }
}
