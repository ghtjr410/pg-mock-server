package com.pgmock.toss.service;

import com.pgmock.toss.domain.Payment;
import com.pgmock.toss.error.TossErrorTrigger;
import com.pgmock.toss.response.TossPaymentResponse;
import com.pgmock.toss.store.PaymentStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TossPaymentService {

    private final PaymentStore paymentStore;
    private final ConcurrentHashMap<String, Map<String, Object>> idempotencyCache = new ConcurrentHashMap<>();

    public TossPaymentService(PaymentStore paymentStore) {
        this.paymentStore = paymentStore;
    }

    public synchronized ResponseEntity<?> confirm(String paymentKey, String orderId, long amount, String idempotencyKey) {
        if (paymentKey == null || orderId == null || amount <= 0) {
            return ResponseEntity.badRequest().body(
                    TossPaymentResponse.errorBody("INVALID_REQUEST", "paymentKey, orderId, amount는 필수입니다."));
        }

        // 멱등키 캐시
        if (idempotencyKey != null) {
            Map<String, Object> cached = idempotencyCache.get(idempotencyKey);
            if (cached != null) return ResponseEntity.ok(cached);
        }

        // 에러 트리거
        ResponseEntity<?> triggered = TossErrorTrigger.checkConfirmTrigger(orderId);
        if (triggered != null) return triggered;

        // 기존 결제건 검증
        Payment existing = paymentStore.findByPaymentKey(paymentKey);
        if (existing != null) {
            if (existing.getTotalAmount() != amount) {
                return ResponseEntity.badRequest().body(
                        TossPaymentResponse.errorBody("AMOUNT_MISMATCH", "결제 금액이 일치하지 않습니다."));
            }
            if (!existing.getOrderId().equals(orderId)) {
                return ResponseEntity.badRequest().body(
                        TossPaymentResponse.errorBody("INVALID_REQUEST", "주문번호가 일치하지 않습니다."));
            }
            if (existing.isDone()) return ResponseEntity.ok(TossPaymentResponse.toResponse(existing));
        }

        Payment payment = new Payment(paymentKey, orderId, amount);
        payment.approve();
        paymentStore.save(payment);

        Map<String, Object> response = TossPaymentResponse.toResponse(payment);
        if (idempotencyKey != null) idempotencyCache.put(idempotencyKey, response);

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> getPayment(String paymentKey) {
        Payment payment = paymentStore.findByPaymentKey(paymentKey);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    TossPaymentResponse.errorBody("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
        }
        return ResponseEntity.ok(TossPaymentResponse.toResponse(payment));
    }

    public ResponseEntity<?> getPaymentByOrderId(String orderId) {
        Payment payment = paymentStore.findByOrderId(orderId);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    TossPaymentResponse.errorBody("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
        }
        return ResponseEntity.ok(TossPaymentResponse.toResponse(payment));
    }

    public synchronized ResponseEntity<?> cancel(String paymentKey, String cancelReason, Long cancelAmount,
                                                  String idempotencyKey) {
        // 취소 멱등키 캐시
        if (idempotencyKey != null) {
            Map<String, Object> cached = idempotencyCache.get(idempotencyKey);
            if (cached != null) return ResponseEntity.ok(cached);
        }

        Payment payment = paymentStore.findByPaymentKey(paymentKey);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    TossPaymentResponse.errorBody("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
        }
        if (!payment.isCancelable()) {
            return ResponseEntity.badRequest().body(
                    TossPaymentResponse.errorBody("ALREADY_CANCELED_PAYMENT", "이미 취소된 결제 입니다"));
        }

        // 부분취소 금액 검증
        if (cancelAmount != null && cancelAmount > payment.getBalanceAmount()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    TossPaymentResponse.errorBody("NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액입니다"));
        }

        // 취소 에러 트리거
        ResponseEntity<?> cancelTriggered = TossErrorTrigger.checkCancelTrigger(cancelReason);
        if (cancelTriggered != null) return cancelTriggered;

        payment.cancel(cancelReason, cancelAmount);

        Map<String, Object> response = TossPaymentResponse.toResponse(payment);
        if (idempotencyKey != null) idempotencyCache.put(idempotencyKey, response);

        return ResponseEntity.ok(response);
    }
}
