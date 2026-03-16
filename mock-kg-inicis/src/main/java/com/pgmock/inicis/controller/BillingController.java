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

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

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

    public record PayRequest(String billingKey, String orderId, long amount,
                             String productName, String buyerName) {}

    public record InquiryRequest(String tid) {}
}
