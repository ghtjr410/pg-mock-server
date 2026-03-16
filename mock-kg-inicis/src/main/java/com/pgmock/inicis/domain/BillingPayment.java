package com.pgmock.inicis.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BillingPayment {

    private static final DateTimeFormatter TID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String tid;
    private final String billingKey;
    private final String orderId;
    private final long amount;
    private final String productName;
    private final String buyerName;
    private final String resultCode;
    private final String resultMsg;
    private final String approvedAt;
    private final String cardNo;

    private BillingPayment(String tid, String billingKey, String orderId, long amount,
                           String productName, String buyerName,
                           String resultCode, String resultMsg, String approvedAt, String cardNo) {
        this.tid = tid;
        this.billingKey = billingKey;
        this.orderId = orderId;
        this.amount = amount;
        this.productName = productName;
        this.buyerName = buyerName;
        this.resultCode = resultCode;
        this.resultMsg = resultMsg;
        this.approvedAt = approvedAt;
        this.cardNo = cardNo;
    }

    public static BillingPayment success(String billingKey, String orderId, long amount,
                                         String productName, String buyerName) {
        String now = LocalDateTime.now().format(TID_FORMATTER);
        String tid = "INI" + now + String.format("%03d", (int) (Math.random() * 1000));
        return new BillingPayment(tid, billingKey, orderId, amount, productName, buyerName,
                "00", "정상", now, "1234-****-****-5678");
    }

    public static BillingPayment fail(String orderId, String resultCode, String resultMsg) {
        return new BillingPayment(null, null, orderId, 0, null, null,
                resultCode, resultMsg, null, null);
    }

    public String getTid() { return tid; }
    public String getBillingKey() { return billingKey; }
    public String getOrderId() { return orderId; }
    public long getAmount() { return amount; }
    public String getProductName() { return productName; }
    public String getBuyerName() { return buyerName; }
    public String getResultCode() { return resultCode; }
    public String getResultMsg() { return resultMsg; }
    public String getApprovedAt() { return approvedAt; }
    public String getCardNo() { return cardNo; }

    public boolean isSuccess() { return "00".equals(resultCode); }
}
