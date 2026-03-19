package com.pgmock.common.chaos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 카오스 모드에 따라 요청을 가로채서 지연/실패/타임아웃을 시뮬레이션한다.
 * /chaos/** 경로는 인터셉트하지 않는다.
 */
public class ChaosInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ChaosInterceptor.class);

    // PARTIAL_FAILURE 에러코드 풀 — 재시도 가능 70%, 불가 30%
    private static final List<ErrorSpec> RETRYABLE_ERRORS = List.of(
            new ErrorSpec(500, "FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다"),
            new ErrorSpec(400, "PROVIDER_ERROR", "일시적인 오류가 발생했습니다"),
            new ErrorSpec(500, "UNKNOWN_PAYMENT_ERROR", "결제에 실패했어요")
    );
    private static final List<ErrorSpec> NON_RETRYABLE_ERRORS = List.of(
            new ErrorSpec(403, "REJECT_CARD_COMPANY", "결제 승인이 거절되었습니다"),
            new ErrorSpec(403, "REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족"),
            new ErrorSpec(403, "FORBIDDEN_REQUEST", "허용되지 않은 요청입니다")
    );

    private final ChaosProperties properties;

    public ChaosInterceptor(ChaosProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/chaos") || path.startsWith("/test")) {
            return true;
        }

        // GET(조회) 요청은 기본적으로 카오스 적용하지 않음
        // → 실무 시나리오: confirm 타임아웃 → 조회로 상태 확인
        if ("GET".equalsIgnoreCase(request.getMethod()) && !properties.isAffectReadApis()) {
            return true;
        }

        // 헤더로 모드 오버라이드 가능
        ChaosMode mode = resolveMode(request);

        switch (mode) {
            case NORMAL -> {
                return true;
            }
            case SLOW -> {
                int delay = ThreadLocalRandom.current().nextInt(properties.getSlowMinMs(), properties.getSlowMaxMs() + 1);
                log.info("[CHAOS:SLOW] {}ms 지연 적용", delay);
                Thread.sleep(delay);
                return true;
            }
            case TIMEOUT -> {
                log.info("[CHAOS:TIMEOUT] 무한 대기 시뮬레이션 (5분)");
                Thread.sleep(300_000);
                return true;
            }
            case DEAD -> {
                log.info("[CHAOS:DEAD] 즉시 500 반환");
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.setContentType("application/json");
                response.getWriter().write("""
                        {"code":"FAILED_INTERNAL_SYSTEM_PROCESSING","message":"내부 시스템 처리 작업이 실패했습니다"}""");
                return false;
            }
            case PARTIAL_FAILURE -> {
                int roll = ThreadLocalRandom.current().nextInt(100);
                if (roll < properties.getPartialFailureRate()) {
                    // 재시도 가능 70%, 불가 30%
                    ErrorSpec error = pickPartialFailureError();
                    log.info("[CHAOS:PARTIAL_FAILURE] {}% 확률 실패 (roll={}, code={})",
                            properties.getPartialFailureRate(), roll, error.code());
                    response.setStatus(error.httpStatus());
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"code\":\"" + error.code() + "\",\"message\":\"" + error.message() + "\"}");
                    return false;
                }
                return true;
            }
        }
        return true;
    }

    private ErrorSpec pickPartialFailureError() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(100) < 70) {
            return RETRYABLE_ERRORS.get(random.nextInt(RETRYABLE_ERRORS.size()));
        } else {
            return NON_RETRYABLE_ERRORS.get(random.nextInt(NON_RETRYABLE_ERRORS.size()));
        }
    }

    private ChaosMode resolveMode(HttpServletRequest request) {
        String headerMode = request.getHeader("X-CHAOS-MODE");
        if (headerMode != null) {
            try {
                return ChaosMode.valueOf(headerMode.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return properties.getMode();
    }

    private record ErrorSpec(int httpStatus, String code, String message) {}
}
