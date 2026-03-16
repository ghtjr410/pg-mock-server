package com.pgmock.toss.controller;

import com.pgmock.toss.domain.Payment;
import com.pgmock.toss.store.PaymentStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/v1/payments")
public class TossPaymentController {

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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ConfirmRequest request) {

        if (request.paymentKey() == null || request.orderId() == null || request.amount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_REQUEST",
                    "message", "paymentKey, orderId, amount는 필수입니다."
            ));
        }

        // 멱등키가 있으면 캐시된 응답 반환
        if (idempotencyKey != null) {
            Map<String, Object> cached = idempotencyCache.get(idempotencyKey);
            if (cached != null) {
                return ResponseEntity.ok(cached);
            }
        }

        // 기존 결제건 조회 — 금액 불일치 검증
        Payment existing = paymentStore.findByPaymentKey(request.paymentKey());
        if (existing != null) {
            if (existing.getAmount() != request.amount()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", "AMOUNT_MISMATCH",
                        "message", "결제 금액이 일치하지 않습니다."
                ));
            }
            if (!existing.getOrderId().equals(request.orderId())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", "INVALID_REQUEST",
                        "message", "주문번호가 일치하지 않습니다."
                ));
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
    public ResponseEntity<?> getPayment(@PathVariable String paymentKey) {
        Payment payment = paymentStore.findByPaymentKey(paymentKey);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "NOT_FOUND_PAYMENT",
                    "message", "존재하지 않는 결제입니다."
            ));
        }
        return ResponseEntity.ok(toResponse(payment));
    }

    /**
     * 결제 취소
     */
    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String paymentKey, @RequestBody CancelRequest request) {
        Payment payment = paymentStore.findByPaymentKey(paymentKey);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "NOT_FOUND_PAYMENT",
                    "message", "존재하지 않는 결제입니다."
            ));
        }
        if (!payment.isDone()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "ALREADY_CANCELED_PAYMENT",
                    "message", "이미 취소된 결제입니다."
            ));
        }

        payment.cancel(request.cancelReason());
        return ResponseEntity.ok(toResponse(payment));
    }

    private Map<String, Object> toResponse(Payment p) {
        var map = new java.util.LinkedHashMap<String, Object>();
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
}
