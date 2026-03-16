package com.pgmock.common.chaos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 카오스 모드에 따라 요청을 가로채서 지연/실패/타임아웃을 시뮬레이션한다.
 * /chaos/** 경로는 인터셉트하지 않는다.
 */
public class ChaosInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ChaosInterceptor.class);
    private final ChaosProperties properties;

    public ChaosInterceptor(ChaosProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/chaos")) {
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
                        {"code":"PROVIDER_ERROR","message":"PG 내부 오류 (카오스 모드)"}""");
                return false;
            }
            case PARTIAL_FAILURE -> {
                int roll = ThreadLocalRandom.current().nextInt(100);
                if (roll < properties.getPartialFailureRate()) {
                    log.info("[CHAOS:PARTIAL_FAILURE] {}% 확률 실패 (roll={})", properties.getPartialFailureRate(), roll);
                    response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                    response.setContentType("application/json");
                    response.getWriter().write("""
                            {"code":"PROVIDER_ERROR","message":"간헐적 오류 (카오스 모드)"}""");
                    return false;
                }
                return true;
            }
        }
        return true;
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
}
