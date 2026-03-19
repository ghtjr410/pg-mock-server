package com.pgmock.toss.response;

import com.pgmock.toss.domain.Payment;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TossPaymentResponse {

    private TossPaymentResponse() {}

    public static Map<String, Object> toResponse(Payment p) {
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
                cm.put("taxFreeAmount", cancel.taxFreeAmount());
                cm.put("taxExemptionAmount", cancel.taxExemptionAmount());
                cm.put("refundableAmount", cancel.refundableAmount());
                cm.put("cardDiscountAmount", cancel.cardDiscountAmount());
                cm.put("transferDiscountAmount", cancel.transferDiscountAmount());
                cm.put("easyPayDiscountAmount", cancel.easyPayDiscountAmount());
                cm.put("canceledAt", Payment.formatDateTime(cancel.canceledAt()));
                cm.put("transactionKey", cancel.transactionKey());
                cm.put("receiptKey", cancel.receiptKey());
                cm.put("cancelStatus", cancel.cancelStatus());
                cm.put("cancelRequestId", cancel.cancelRequestId());
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

        // receipt
        map.put("receipt", Map.of("url", "https://mock-receipt.tosspayments.com/" + p.getPaymentKey()));
        // checkout
        map.put("checkout", Map.of("url", "https://mock-checkout.tosspayments.com/" + p.getPaymentKey()));

        return map;
    }

    public static Map<String, Object> errorBody(String code, String message) {
        return Map.of("code", code, "message", message);
    }
}
