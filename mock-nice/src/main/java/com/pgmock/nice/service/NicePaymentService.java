package com.pgmock.nice.service;

import com.pgmock.nice.domain.Payment;
import com.pgmock.nice.error.NiceErrorTrigger;
import com.pgmock.nice.response.NicePaymentResponse;
import com.pgmock.nice.store.PaymentStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NicePaymentService {

    private final PaymentStore paymentStore;

    public NicePaymentService(PaymentStore paymentStore) {
        this.paymentStore = paymentStore;
    }

    public synchronized ResponseEntity<?> approve(String tid, long amount) {
        if (tid == null || tid.isBlank() || amount <= 0) {
            return ResponseEntity.badRequest().body(
                    NicePaymentResponse.errorBody("9000", "필수 필드값이 누락되었습니다."));
        }

        // 기존 결제건 검증
        Payment existing = paymentStore.findByTid(tid);
        if (existing != null) {
            if (existing.getAmount() != amount) {
                return ResponseEntity.badRequest().body(
                        NicePaymentResponse.errorBody("A123", "거래금액 불일치(인증된 금액과 승인요청 금액 불일치)"));
            }
            if (existing.isPaid()) {
                return ResponseEntity.ok(NicePaymentResponse.toResponse(existing));
            }
        }

        // 에러 트리거 (orderId = ORDER-{tid})
        String orderId = "ORDER-" + tid;
        ResponseEntity<?> triggered = NiceErrorTrigger.checkApproveTrigger(orderId);
        if (triggered != null) return triggered;

        Payment payment = new Payment(tid, amount);
        payment.approve();
        paymentStore.save(payment);

        return ResponseEntity.ok(NicePaymentResponse.toResponse(payment));
    }

    public ResponseEntity<?> getPaymentByTid(String tid) {
        Payment payment = paymentStore.findByTid(tid);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    NicePaymentResponse.errorBody("A118", "조회 결과데이터 없음"));
        }
        return ResponseEntity.ok(NicePaymentResponse.toResponse(payment));
    }

    public ResponseEntity<?> getPaymentByOrderId(String orderId) {
        Payment payment = paymentStore.findByOrderId(orderId);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    NicePaymentResponse.errorBody("A118", "조회 결과데이터 없음"));
        }
        return ResponseEntity.ok(NicePaymentResponse.toResponse(payment));
    }

    public synchronized ResponseEntity<?> cancel(String tid, String reason, String orderId, Long cancelAmt) {
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(
                    NicePaymentResponse.errorBody("9000", "reason 필드값이 누락되었습니다."));
        }
        if (orderId == null || orderId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    NicePaymentResponse.errorBody("9000", "orderId 필드값이 누락되었습니다."));
        }

        Payment payment = paymentStore.findByTid(tid);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    NicePaymentResponse.errorBody("2012", "취소 해당거래 없음"));
        }
        if (!payment.isCancelable()) {
            return ResponseEntity.badRequest().body(
                    NicePaymentResponse.errorBody("2013", "취소 완료 거래"));
        }

        // 부분취소 금액 검증
        if (cancelAmt != null && cancelAmt > payment.getBalanceAmt()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    NicePaymentResponse.errorBody("2032", "취소금액이 취소가능금액보다 큼"));
        }

        // 취소 에러 트리거
        ResponseEntity<?> cancelTriggered = NiceErrorTrigger.checkCancelTrigger(reason);
        if (cancelTriggered != null) return cancelTriggered;

        Payment.Cancel cancel = payment.cancel(reason, cancelAmt);

        Map<String, Object> response = NicePaymentResponse.toResponse(payment);
        response.put("cancelledTid", cancel.tid());

        return ResponseEntity.ok(response);
    }
}
