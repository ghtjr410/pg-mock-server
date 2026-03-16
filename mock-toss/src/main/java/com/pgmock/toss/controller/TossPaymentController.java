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

    // confirm 에러 트리거 — orderId에 키워드 포함 시 해당 에러 반환
    private static final Map<String, ErrorSpec> CONFIRM_ERROR_TRIGGERS = new LinkedHashMap<>();
    // cancel 에러 트리거
    private static final Map<String, ErrorSpec> CANCEL_ERROR_TRIGGERS = new LinkedHashMap<>();

    static {
        // === confirm 재시도 불가 ===
        CONFIRM_ERROR_TRIGGERS.put("already_processed", new ErrorSpec(400, "ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다"));
        CONFIRM_ERROR_TRIGGERS.put("invalid_card", new ErrorSpec(400, "INVALID_CARD_NUMBER", "카드번호를 다시 확인해주세요"));
        CONFIRM_ERROR_TRIGGERS.put("stopped_card", new ErrorSpec(400, "INVALID_STOPPED_CARD", "정지된 카드 입니다"));
        CONFIRM_ERROR_TRIGGERS.put("expired_card", new ErrorSpec(400, "INVALID_CARD_EXPIRATION", "카드 정보를 다시 확인해주세요 (유효기간)"));
        CONFIRM_ERROR_TRIGGERS.put("reject_card", new ErrorSpec(400, "INVALID_REJECT_CARD", "카드 사용이 거절되었습니다"));
        CONFIRM_ERROR_TRIGGERS.put("exceed", new ErrorSpec(400, "EXCEED_MAX_AMOUNT", "거래금액 한도를 초과했습니다"));
        CONFIRM_ERROR_TRIGGERS.put("lost_stolen", new ErrorSpec(400, "INVALID_CARD_LOST_OR_STOLEN", "분실 혹은 도난 카드입니다"));
        CONFIRM_ERROR_TRIGGERS.put("unapproved", new ErrorSpec(400, "UNAPPROVED_ORDER_ID", "아직 승인되지 않은 주문번호입니다"));
        CONFIRM_ERROR_TRIGGERS.put("reject_payment", new ErrorSpec(403, "REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족"));
        CONFIRM_ERROR_TRIGGERS.put("reject_company", new ErrorSpec(403, "REJECT_CARD_COMPANY", "결제 승인이 거절되었습니다"));
        CONFIRM_ERROR_TRIGGERS.put("forbidden", new ErrorSpec(403, "FORBIDDEN_REQUEST", "허용되지 않은 요청입니다"));
        CONFIRM_ERROR_TRIGGERS.put("not_found_session", new ErrorSpec(404, "NOT_FOUND_PAYMENT_SESSION", "결제 시간이 만료됨"));
        // === confirm 재시도 가능 ===
        CONFIRM_ERROR_TRIGGERS.put("provider_error", new ErrorSpec(400, "PROVIDER_ERROR", "일시적인 오류가 발생했습니다"));
        CONFIRM_ERROR_TRIGGERS.put("card_processing", new ErrorSpec(400, "CARD_PROCESSING_ERROR", "카드사에서 오류가 발생했습니다"));
        CONFIRM_ERROR_TRIGGERS.put("system_error", new ErrorSpec(500, "FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다"));
        CONFIRM_ERROR_TRIGGERS.put("payment_processing", new ErrorSpec(500, "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING", "결제가 완료되지 않았어요"));
        CONFIRM_ERROR_TRIGGERS.put("unknown_error", new ErrorSpec(500, "UNKNOWN_PAYMENT_ERROR", "결제에 실패했어요"));

        // === cancel 에러 ===
        CANCEL_ERROR_TRIGGERS.put("not_cancelable_amount", new ErrorSpec(403, "NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액입니다"));
        CANCEL_ERROR_TRIGGERS.put("not_cancelable", new ErrorSpec(403, "NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제입니다"));
        CANCEL_ERROR_TRIGGERS.put("cancel_system_error", new ErrorSpec(500, "FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다"));
        CANCEL_ERROR_TRIGGERS.put("cancel_method_error", new ErrorSpec(500, "FAILED_METHOD_HANDLING_CANCEL", "결제수단 처리 오류입니다"));
    }

    private final PaymentStore paymentStore;
    private final ConcurrentHashMap<String, Map<String, Object>> idempotencyCache = new ConcurrentHashMap<>();

    public TossPaymentController(PaymentStore paymentStore) {
        this.paymentStore = paymentStore;
    }

    /**
     * 결제 승인 (confirm)
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ConfirmRequest request) {

        ResponseEntity<?> authError = validateAuthorization(authorization);
        if (authError != null) return authError;

        if (request.paymentKey() == null || request.orderId() == null || request.amount() <= 0) {
            return ResponseEntity.badRequest().body(errorBody(
                    "INVALID_REQUEST", "paymentKey, orderId, amount는 필수입니다."));
        }

        // 멱등키 캐시
        if (idempotencyKey != null) {
            Map<String, Object> cached = idempotencyCache.get(idempotencyKey);
            if (cached != null) return ResponseEntity.ok(cached);
        }

        // 에러 트리거
        ResponseEntity<?> triggered = checkErrorTrigger(request.orderId(), CONFIRM_ERROR_TRIGGERS);
        if (triggered != null) return triggered;

        // 기존 결제건 검증
        Payment existing = paymentStore.findByPaymentKey(request.paymentKey());
        if (existing != null) {
            if (existing.getTotalAmount() != request.amount()) {
                return ResponseEntity.badRequest().body(errorBody(
                        "AMOUNT_MISMATCH", "결제 금액이 일치하지 않습니다."));
            }
            if (!existing.getOrderId().equals(request.orderId())) {
                return ResponseEntity.badRequest().body(errorBody(
                        "INVALID_REQUEST", "주문번호가 일치하지 않습니다."));
            }
            if (existing.isDone()) return ResponseEntity.ok(toResponse(existing));
        }

        Payment payment = new Payment(request.paymentKey(), request.orderId(), request.amount());
        payment.approve();
        paymentStore.save(payment);

        Map<String, Object> response = toResponse(payment);
        if (idempotencyKey != null) idempotencyCache.put(idempotencyKey, response);

        return ResponseEntity.ok(response);
    }

    /**
     * paymentKey로 결제 조회
     */
    @GetMapping("/{paymentKey}")
    public ResponseEntity<?> getPayment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String paymentKey) {

        ResponseEntity<?> authError = validateAuthorization(authorization);
        if (authError != null) return authError;

        Payment payment = paymentStore.findByPaymentKey(paymentKey);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
        }
        return ResponseEntity.ok(toResponse(payment));
    }

    /**
     * orderId로 결제 조회
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getPaymentByOrderId(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String orderId) {

        ResponseEntity<?> authError = validateAuthorization(authorization);
        if (authError != null) return authError;

        Payment payment = paymentStore.findByOrderId(orderId);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
        }
        return ResponseEntity.ok(toResponse(payment));
    }

    /**
     * 결제 취소 (전액/부분)
     */
    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<?> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String paymentKey,
            @RequestBody CancelRequest request) {

        ResponseEntity<?> authError = validateAuthorization(authorization);
        if (authError != null) return authError;

        Payment payment = paymentStore.findByPaymentKey(paymentKey);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
        }
        if (!payment.isCancelable()) {
            return ResponseEntity.badRequest().body(errorBody(
                    "ALREADY_CANCELED_PAYMENT", "이미 취소된 결제 입니다"));
        }

        // 부분취소 금액 검증
        if (request.cancelAmount() != null && request.cancelAmount() > payment.getBalanceAmount()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(
                    "NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액입니다"));
        }

        // 취소 에러 트리거
        ResponseEntity<?> cancelTriggered = checkErrorTrigger(request.cancelReason(), CANCEL_ERROR_TRIGGERS);
        if (cancelTriggered != null) return cancelTriggered;

        payment.cancel(request.cancelReason(), request.cancelAmount());
        return ResponseEntity.ok(toResponse(payment));
    }

    // === private helpers ===

    private ResponseEntity<?> validateAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(
                    "UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다."));
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

    private ResponseEntity<?> checkErrorTrigger(String value, Map<String, ErrorSpec> triggers) {
        if (value == null) return null;
        String lower = value.toLowerCase();
        for (var entry : triggers.entrySet()) {
            if (lower.contains(entry.getKey())) {
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
        map.put("version", p.getVersion());
        map.put("paymentKey", p.getPaymentKey());
        map.put("type", p.getType());
        map.put("orderId", p.getOrderId());
        map.put("orderName", p.getOrderName());
        map.put("mId", p.getMId());
        map.put("currency", p.getCurrency());
        map.put("method", p.getMethod());
        map.put("totalAmount", p.getTotalAmount());
        map.put("balanceAmount", p.getBalanceAmount());
        map.put("suppliedAmount", p.getSuppliedAmount());
        map.put("vat", p.getVat());
        map.put("taxFreeAmount", p.getTaxFreeAmount());
        map.put("taxExemptionAmount", p.getTaxExemptionAmount());
        map.put("status", p.getStatus());
        map.put("requestedAt", Payment.formatDateTime(p.getRequestedAt()));
        map.put("approvedAt", Payment.formatDateTime(p.getApprovedAt()));
        map.put("useEscrow", p.isUseEscrow());
        map.put("cultureExpense", p.isCultureExpense());
        map.put("lastTransactionKey", p.getLastTransactionKey());
        map.put("country", p.getCountry());
        map.put("isPartialCancelable", p.isPartialCancelable());

        // card
        Payment.Card c = p.getCard();
        var cardMap = new LinkedHashMap<String, Object>();
        cardMap.put("issuerCode", c.issuerCode());
        cardMap.put("acquirerCode", c.acquirerCode());
        cardMap.put("number", c.number());
        cardMap.put("installmentPlanMonths", c.installmentPlanMonths());
        cardMap.put("isInterestFree", c.isInterestFree());
        cardMap.put("interestPayer", c.interestPayer());
        cardMap.put("approveNo", c.approveNo());
        cardMap.put("useCardPoint", false);
        cardMap.put("cardType", c.cardType());
        cardMap.put("ownerType", c.ownerType());
        cardMap.put("acquireStatus", c.acquireStatus());
        cardMap.put("amount", c.amount());
        map.put("card", cardMap);

        // cancels
        if (!p.getCancels().isEmpty()) {
            var cancelList = p.getCancels().stream().map(cancel -> {
                var cm = new LinkedHashMap<String, Object>();
                cm.put("cancelAmount", cancel.cancelAmount());
                cm.put("cancelReason", cancel.cancelReason());
                cm.put("canceledAt", cancel.canceledAt().toString());
                cm.put("transactionKey", cancel.transactionKey());
                cm.put("refundableAmount", cancel.refundableAmount());
                return (Map<String, Object>) cm;
            }).toList();
            map.put("cancels", cancelList);
        } else {
            map.put("cancels", null);
        }

        // nullable fields
        map.put("virtualAccount", null);
        map.put("transfer", null);
        map.put("mobilePhone", null);
        map.put("giftCertificate", null);
        map.put("cashReceipt", null);
        map.put("cashReceipts", null);
        map.put("discount", null);
        map.put("easyPay", null);
        map.put("failure", null);

        // receipt — 실무 코드가 receipt.url에 접근할 수 있으므로 구조 유지
        map.put("receipt", Map.of("url", "https://mock-receipt.tosspayments.com/" + p.getPaymentKey()));
        // checkout
        map.put("checkout", Map.of("url", "https://mock-checkout.tosspayments.com/" + p.getPaymentKey()));

        return map;
    }

    public record ConfirmRequest(String paymentKey, String orderId, long amount) {}
    public record CancelRequest(String cancelReason, Long cancelAmount) {}

    private record ErrorSpec(int httpStatus, String code, String message) {}
}
