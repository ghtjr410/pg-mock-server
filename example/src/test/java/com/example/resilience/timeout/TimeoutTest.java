package com.example.resilience.timeout;

import com.example.resilience.ExampleTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeoutTest extends ExampleTestBase {

    @Test
    @DisplayName("TIMEOUT → readTimeout(2s) 초과 → ResourceAccessException, ~2s 소요")
    void timeout_throwsResourceAccessException() {
        paymentClient.setChaosMode("TIMEOUT");
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 2000); // readTimeout=2s

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> paymentClient.confirm("pk_timeout", "order_timeout", 10000))
                .isInstanceOf(ResourceAccessException.class);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isBetween(1500L, 3000L); // ~2s
    }

    @Test
    @DisplayName("SLOW(2s) + readTimeout(5s) → 성공, elapsed >= 2s")
    void slow_withinTimeout_succeeds() {
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "2000", "slowMaxMs", "2000"));
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 5000); // readTimeout=5s

        long start = System.currentTimeMillis();
        Map<String, Object> result = paymentClient.confirm("pk_slow_ok", "order_slow_ok", 10000);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(elapsed).isGreaterThanOrEqualTo(2000L);
    }

    @Test
    @DisplayName("SLOW(2s) + readTimeout(1s) → ResourceAccessException")
    void slow_exceedsTimeout_throws() {
        paymentClient.setChaosMode("SLOW", Map.of("slowMinMs", "2000", "slowMaxMs", "2000"));
        paymentClient.configure(
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090),
                5000, 1000); // readTimeout=1s

        assertThatThrownBy(() -> paymentClient.confirm("pk_slow_fail", "order_slow_fail", 10000))
                .isInstanceOf(ResourceAccessException.class);
    }
}
