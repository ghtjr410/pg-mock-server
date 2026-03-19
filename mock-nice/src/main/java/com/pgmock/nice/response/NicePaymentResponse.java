package com.pgmock.nice.response;

import com.pgmock.nice.domain.Payment;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NicePaymentResponse {

    private NicePaymentResponse() {}

    public static Map<String, Object> toResponse(Payment p) {
        var map = new LinkedHashMap<String, Object>();
        map.put("resultCode", "0000");
        map.put("resultMsg", "정상 처리되었습니다.");
        map.put("tid", p.getTid());
        map.put("orderId", p.getOrderId());
        map.put("ediDate", Payment.formatDateTime(p.getPaidAt()));
        map.put("signature", null);
        map.put("status", p.getStatus());
        map.put("paidAt", Payment.formatDateTime(p.getPaidAt()));
        map.put("failedAt", "0");
        map.put("cancelledAt", Payment.formatDateTime(p.getCancelledAt()));
        map.put("payMethod", p.getPayMethod());
        map.put("amount", p.getAmount());
        map.put("balanceAmt", p.getBalanceAmt());
        map.put("goodsName", p.getGoodsName());
        map.put("useEscrow", p.isUseEscrow());
        map.put("currency", p.getCurrency());
        map.put("channel", p.getChannel());
        map.put("approveNo", p.getApproveNo());
        map.put("mallReserved", null);
        map.put("mallUserId", null);
        map.put("buyerName", null);
        map.put("buyerTel", null);
        map.put("buyerEmail", null);
        map.put("issuedCashReceipt", false);
        map.put("receiptUrl", "https://mock-receipt.nicepay.co.kr/" + p.getTid());
        map.put("cancelledTid", null);

        // card
        Payment.Card c = p.getCard();
        var cardMap = new LinkedHashMap<String, Object>();
        cardMap.put("cardCode", c.cardCode());
        cardMap.put("cardName", c.cardName());
        cardMap.put("cardNum", c.cardNum());
        cardMap.put("cardQuota", c.cardQuota());
        cardMap.put("isInterestFree", c.isInterestFree());
        cardMap.put("cardType", c.cardType());
        cardMap.put("canPartCancel", c.canPartCancel());
        cardMap.put("acquCardCode", c.acquCardCode());
        cardMap.put("acquCardName", c.acquCardName());
        map.put("card", cardMap);

        // cancels
        if (!p.getCancels().isEmpty()) {
            var cancelList = p.getCancels().stream().map(cancel -> {
                var cm = new LinkedHashMap<String, Object>();
                cm.put("tid", cancel.tid());
                cm.put("amount", cancel.amount());
                cm.put("cancelledAt", Payment.formatDateTime(cancel.cancelledAt()));
                cm.put("reason", cancel.reason());
                cm.put("receiptUrl", "https://mock-receipt.nicepay.co.kr/" + cancel.tid());
                cm.put("couponAmt", 0);
                return (Map<String, Object>) cm;
            }).toList();
            map.put("cancels", cancelList);
        } else {
            map.put("cancels", null);
        }

        // nullable
        map.put("bank", null);
        map.put("vbank", null);
        map.put("cashReceipts", null);
        map.put("coupon", null);

        return map;
    }

    public static Map<String, Object> errorBody(String resultCode, String resultMsg) {
        var map = new LinkedHashMap<String, Object>();
        map.put("resultCode", resultCode);
        map.put("resultMsg", resultMsg);
        return map;
    }
}
