package com.pgmock.common.chaos;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosInterceptorTest {

    @Test
    void NORMAL모드_통과() throws Exception {
        ChaosProperties props = new ChaosProperties();
        props.setMode(ChaosMode.NORMAL);
        ChaosInterceptor interceptor = new ChaosInterceptor(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/payments/confirm");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isTrue();
    }

    @Test
    void DEAD모드_500반환() throws Exception {
        ChaosProperties props = new ChaosProperties();
        props.setMode(ChaosMode.DEAD);
        ChaosInterceptor interceptor = new ChaosInterceptor(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/payments/confirm");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void 카오스경로_우회() throws Exception {
        ChaosProperties props = new ChaosProperties();
        props.setMode(ChaosMode.DEAD);
        ChaosInterceptor interceptor = new ChaosInterceptor(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/chaos/mode");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isTrue();
    }

    @Test
    void 헤더_모드오버라이드() throws Exception {
        ChaosProperties props = new ChaosProperties();
        props.setMode(ChaosMode.NORMAL);
        ChaosInterceptor interceptor = new ChaosInterceptor(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/payments/confirm");
        request.addHeader("X-CHAOS-MODE", "DEAD");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(500);
    }
}
