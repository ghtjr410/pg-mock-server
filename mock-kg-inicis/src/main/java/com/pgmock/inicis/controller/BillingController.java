package com.pgmock.inicis.controller;

import com.pgmock.inicis.domain.BillingPayment;
import com.pgmock.inicis.store.BillingPaymentStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 에러 시뮬레이션: orderId에 특정 키워드를 포함시키면 해당 에러 반환
 * - "limit" → V110 한도초과
 * - "expired" → V120 카드 유효기간 오류
 * - "badcard" → V130 카드번호 오류
 * - "syserr" → E001 시스템 오류
 */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private static final Map<String, ErrorSpec> ERROR_TRIGGERS = Map.of(
            "limit", new ErrorSpec("V110", "한도초과"),
            "expired", new ErrorSpec("V120", "카드 유효기간 오류"),
            "badcard", new ErrorSpec("V130", "카드번호 오류"),
            "syserr", new ErrorSpec("E001", "시스템 오류")
    );

    private final BillingPaymentStore store;

    public BillingController(BillingPaymentStore store) {
        this.store = store;
    }

    /**
     * 빌링 결제 요청
     */
    @PostMapping("/pay")
    public ResponseEntity<Map<String, Object>> pay(@RequestBody PayRequest request) {
        if (request.billingKey() == null || request.orderId() == null || request.amount() <= 0) {
            return ResponseEntity.badRequest().body(toFailResponse(
                    request.orderId(), "E001", "필수 파라미터가 누락되었습니다."));
        }

        // 에러 트리거 체크
        Map<String, Object> errorResponse = checkErrorTrigger(request.orderId());
        if (errorResponse != null) {
            return ResponseEntity.ok(errorResponse);
        }

        BillingPayment payment = BillingPayment.success(
                request.billingKey(), request.orderId(), request.amount(),
                request.productName(), request.buyerName());
        store.save(payment);

        return ResponseEntity.ok(toSuccessResponse(payment));
    }

    /**
     * 거래 조회
     */
    @PostMapping("/inquiry")
    public ResponseEntity<Map<String, Object>> inquiry(@RequestBody InquiryRequest request) {
        if (request.tid() == null) {
            return ResponseEntity.badRequest().body(toFailResponse(
                    null, "E001", "tid는 필수입니다."));
        }

        BillingPayment payment = store.findByTid(request.tid());
        if (payment == null) {
            return ResponseEntity.ok(toFailResponse(null, "E001", "거래 내역이 존재하지 않습니다."));
        }

        return ResponseEntity.ok(toSuccessResponse(payment));
    }

    private Map<String, Object> toSuccessResponse(BillingPayment p) {
        var map = new LinkedHashMap<String, Object>();
        map.put("resultCode", p.getResultCode());
        map.put("resultMsg", p.getResultMsg());
        map.put("tid", p.getTid());
        map.put("orderId", p.getOrderId());
        map.put("amount", p.getAmount());
        map.put("approvedAt", p.getApprovedAt());
        map.put("cardNo", p.getCardNo());
        return map;
    }

    private Map<String, Object> toFailResponse(String orderId, String resultCode, String resultMsg) {
        var map = new LinkedHashMap<String, Object>();
        map.put("resultCode", resultCode);
        map.put("resultMsg", resultMsg);
        map.put("tid", null);
        map.put("orderId", orderId);
        return map;
    }

    private Map<String, Object> checkErrorTrigger(String orderId) {
        if (orderId == null) return null;
        String lower = orderId.toLowerCase();
        for (var entry : ERROR_TRIGGERS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                ErrorSpec spec = entry.getValue();
                return toFailResponse(orderId, spec.code(), spec.message());
            }
        }
        return null;
    }

    public record PayRequest(String billingKey, String orderId, long amount,
                             String productName, String buyerName) {}

    public record InquiryRequest(String tid) {}

    private record ErrorSpec(String code, String message) {}
}
