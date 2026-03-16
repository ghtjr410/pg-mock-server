package com.pgmock.toss.controller;

import com.pgmock.toss.domain.Payment;
import com.pgmock.toss.store.PaymentStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/v1/payments")
public class TossPaymentController {

    private static final Map<String, ErrorSpec> ERROR_TRIGGERS = Map.of(
            "reject", new ErrorSpec(400, "REJECT_CARD_COMPANY", "카드사에서 거절했습니다."),
            "exceed", new ErrorSpec(400, "EXCEED_MAX_AMOUNT", "한도 초과입니다."),
            "invalid_card", new ErrorSpec(400, "INVALID_CARD_NUMBER", "카드번호가 올바르지 않습니다."),
            "provider_error", new ErrorSpec(500, "PROVIDER_ERROR", "PG 내부 오류입니다."),
            "system_error", new ErrorSpec(500, "FAILED_INTERNAL_SYSTEM_PROCESSING", "시스템 오류가 발생했습니다.")
    );

    private final PaymentStore paymentStore;
    private final ConcurrentHashMap<String, Map<String, Object>> idempotencyCache = new ConcurrentHashMap<>();

    public TossPaymentController(PaymentStore paymentStore) {
        this.paymentStore = paymentStore;
    }

    /**
     * 결제 승인 (confirm)
     *
     * 에러 시뮬레이션: orderId에 특정 키워드를 포함시키면 해당 에러 반환
     * - "reject" → REJECT_CARD_COMPANY (400)
     * - "exceed" → EXCEED_MAX_AMOUNT (400)
     * - "invalid_card" → INVALID_CARD_NUMBER (400)
     * - "provider_error" → PROVIDER_ERROR (500)
     * - "system_error" → FAILED_INTERNAL_SYSTEM_PROCESSING (500)
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ConfirmRequest request) {

        // Authorization 검증
        ResponseEntity<?> authError = validateAuthorization(authorization);
        if (authError != null) {
            return authError;
        }

        if (request.paymentKey() == null || request.orderId() == null || request.amount() <= 0) {
            return ResponseEntity.badRequest().body(errorBody(
                    "INVALID_REQUEST", "paymentKey, orderId, amount는 필수입니다."));
        }

        // 멱등키가 있으면 캐시된 응답 반환
        if (idempotencyKey != null) {
            Map<String, Object> cached = idempotencyCache.get(idempotencyKey);
            if (cached != null) {
                return ResponseEntity.ok(cached);
            }
        }

        // 에러 트리거 체크 (orderId 기반)
        ResponseEntity<?> triggered = checkErrorTrigger(request.orderId());
        if (triggered != null) {
            return triggered;
        }

        // 기존 결제건 조회 — 금액/주문번호 불일치 검증
        Payment existing = paymentStore.findByPaymentKey(request.paymentKey());
        if (existing != null) {
            if (existing.getAmount() != request.amount()) {
                return ResponseEntity.badRequest().body(errorBody(
                        "AMOUNT_MISMATCH", "결제 금액이 일치하지 않습니다."));
            }
            if (!existing.getOrderId().equals(request.orderId())) {
                return ResponseEntity.badRequest().body(errorBody(
                        "INVALID_REQUEST", "주문번호가 일치하지 않습니다."));
            }
            if (existing.isDone()) {
                return ResponseEntity.ok(toResponse(existing));
            }
        }

        // 새 결제건 생성 및 승인
        Payment payment = new Payment(request.paymentKey(), request.orderId(), request.amount());
        payment.approve();
        paymentStore.save(payment);

        Map<String, Object> response = toResponse(payment);

        // 멱등키 캐싱
        if (idempotencyKey != null) {
            idempotencyCache.put(idempotencyKey, response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 결제 조회
     */
    @GetMapping("/{paymentKey}")
    public ResponseEntity<?> getPayment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String paymentKey) {

        ResponseEntity<?> authError = validateAuthorization(authorization);
        if (authError != null) {
            return authError;
        }

        Payment payment = paymentStore.findByPaymentKey(paymentKey);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "NOT_FOUND_PAYMENT", "존재하지 않는 결제입니다."));
        }
        return ResponseEntity.ok(toResponse(payment));
    }

    /**
     * 결제 취소
     */
    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<?> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String paymentKey,
            @RequestBody CancelRequest request) {

        ResponseEntity<?> authError = validateAuthorization(authorization);
        if (authError != null) {
            return authError;
        }

        Payment payment = paymentStore.findByPaymentKey(paymentKey);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "NOT_FOUND_PAYMENT", "존재하지 않는 결제입니다."));
        }
        if (!payment.isDone()) {
            return ResponseEntity.badRequest().body(errorBody(
                    "ALREADY_CANCELED_PAYMENT", "이미 취소된 결제입니다."));
        }

        payment.cancel(request.cancelReason());
        return ResponseEntity.ok(toResponse(payment));
    }

    private ResponseEntity<?> validateAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(
                    "UNAUTHORIZED_KEY", "인증 키가 유효하지 않습니다. Basic 인증을 확인해주세요."));
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)));
            if (!decoded.endsWith(":")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(
                        "UNAUTHORIZED_KEY", "시크릿 키 형식이 올바르지 않습니다. {secretKey}: 형식이어야 합니다."));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(
                    "UNAUTHORIZED_KEY", "Base64 디코딩에 실패했습니다."));
        }
        return null;
    }

    private ResponseEntity<?> checkErrorTrigger(String orderId) {
        if (orderId == null) return null;
        String lowerOrderId = orderId.toLowerCase();
        for (var entry : ERROR_TRIGGERS.entrySet()) {
            if (lowerOrderId.contains(entry.getKey())) {
                ErrorSpec spec = entry.getValue();
                return ResponseEntity.status(spec.httpStatus()).body(errorBody(spec.code(), spec.message()));
            }
        }
        return null;
    }

    private Map<String, Object> errorBody(String code, String message) {
        return Map.of("code", code, "message", message);
    }

    private Map<String, Object> toResponse(Payment p) {
        var map = new LinkedHashMap<String, Object>();
        map.put("paymentKey", p.getPaymentKey());
        map.put("orderId", p.getOrderId());
        map.put("status", p.getStatus());
        map.put("totalAmount", p.getAmount());
        map.put("method", p.getMethod());
        map.put("approvedAt", p.getApprovedAt() != null ? p.getApprovedAt().toString() : null);
        map.put("card", Map.of(
                "issuerCode", p.getCard().issuerCode(),
                "number", p.getCard().number(),
                "approveNo", p.getCard().approveNo()
        ));
        if (p.getCancelReason() != null) {
            map.put("cancelReason", p.getCancelReason());
        }
        return map;
    }

    public record ConfirmRequest(String paymentKey, String orderId, long amount) {}
    public record CancelRequest(String cancelReason) {}

    private record ErrorSpec(int httpStatus, String code, String message) {}
}
