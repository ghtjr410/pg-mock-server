package com.example.resilience.retry;

import com.example.resilience.ExampleTestBase;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryExceptionFilterTest extends ExampleTestBase {

    @Test
    @DisplayName("7-1: retryExceptions에 포함된 예외만 재시도 — timeout O, 500 X")
    void retryOnlyResourceAccessException_notServerError() {
        paymentClient.setChaosMode("DEAD"); // 500 응답

        Retry retry = Retry.of("filter-rae-" + UUID.randomUUID(), RetryConfig.custom()
                .maxAttempts(3)
                .retryExceptions(ResourceAccessException.class) // timeout만 재시도
                .build());

        Supplier<Map<String, Object>> decorated = Retry.decorateSupplier(retry,
                () -> paymentClient.confirm("pk_filter1", "order_filter1", 10000));

        // HttpServerErrorException은 retryExceptions에 없으므로 재시도 없이 즉시 실패
        assertThatThrownBy(decorated::get).isInstanceOf(HttpServerErrorException.class);
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("7-2: 비즈니스 에러(403)는 재시도 안 함")
    void businessError403_noRetry() {
        paymentClient.setChaosMode("NORMAL");

        Retry retry = Retry.of("filter-biz-" + UUID.randomUUID(), RetryConfig.custom()
                .maxAttempts(3)
                .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build());

        // reject_company → 403
        Supplier<Map<String, Object>> decorated = Retry.decorateSupplier(retry,
                () -> paymentClient.confirm("pk_filter2", "reject_company", 10000));

        // HttpClientErrorException은 retryExceptions에 없으므로 재시도 없이 즉시 실패
        assertThatThrownBy(decorated::get).isInstanceOf(HttpClientErrorException.class);
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(1);
    }
}
