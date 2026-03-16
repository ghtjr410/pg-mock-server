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
    void DEAD모드_500_FAILED_INTERNAL() throws Exception {
        ChaosProperties props = new ChaosProperties();
        props.setMode(ChaosMode.DEAD);
        ChaosInterceptor interceptor = new ChaosInterceptor(props);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments/confirm");
        request.setRequestURI("/v1/payments/confirm");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("FAILED_INTERNAL_SYSTEM_PROCESSING");
    }

    @Test
    void PARTIAL_FAILURE모드_100퍼센트실패() throws Exception {
        ChaosProperties props = new ChaosProperties();
        props.setMode(ChaosMode.PARTIAL_FAILURE);
        props.setPartialFailureRate(100);
        ChaosInterceptor interceptor = new ChaosInterceptor(props);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments/confirm");
        request.setRequestURI("/v1/payments/confirm");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isFalse();
        // 에러코드가 풀에서 나온 것인지 확인
        String content = response.getContentAsString();
        assertThat(content).containsAnyOf(
                "FAILED_INTERNAL_SYSTEM_PROCESSING", "PROVIDER_ERROR", "UNKNOWN_PAYMENT_ERROR",
                "REJECT_CARD_COMPANY", "REJECT_CARD_PAYMENT", "FORBIDDEN_REQUEST");
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
    void DEAD모드에서_GET요청_기본우회() throws Exception {
        ChaosProperties props = new ChaosProperties();
        props.setMode(ChaosMode.DEAD);
        // affectReadApis 기본값 false
        ChaosInterceptor interceptor = new ChaosInterceptor(props);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments/tpk_xxx");
        request.setRequestURI("/v1/payments/tpk_xxx");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isTrue(); // GET은 카오스 적용 안됨
    }

    @Test
    void DEAD모드에서_GET요청_affectReadApis_true면_적용() throws Exception {
        ChaosProperties props = new ChaosProperties();
        props.setMode(ChaosMode.DEAD);
        props.setAffectReadApis(true);
        ChaosInterceptor interceptor = new ChaosInterceptor(props);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments/tpk_xxx");
        request.setRequestURI("/v1/payments/tpk_xxx");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isFalse(); // affectReadApis=true면 GET도 카오스 적용
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void 헤더_모드오버라이드() throws Exception {
        ChaosProperties props = new ChaosProperties();
        props.setMode(ChaosMode.NORMAL);
        ChaosInterceptor interceptor = new ChaosInterceptor(props);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments/confirm");
        request.setRequestURI("/v1/payments/confirm");
        request.addHeader("X-CHAOS-MODE", "DEAD");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(500);
    }
}
