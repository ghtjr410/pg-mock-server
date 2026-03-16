package com.pgmock.toss.domain;

import java.time.OffsetDateTime;

public class Payment {

    private final String paymentKey;
    private final String orderId;
    private final long amount;
    private String status;
    private final String method;
    private OffsetDateTime approvedAt;
    private String cancelReason;
    private final Card card;

    public Payment(String paymentKey, String orderId, long amount) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.amount = amount;
        this.status = "READY";
        this.method = "카드";
        this.card = new Card("11", "1234-****-****-5678", generateApproveNo());
    }

    public void approve() {
        this.status = "DONE";
        this.approvedAt = OffsetDateTime.now();
    }

    public void cancel(String reason) {
        this.status = "CANCELED";
        this.cancelReason = reason;
    }

    public boolean isDone() {
        return "DONE".equals(status);
    }

    private String generateApproveNo() {
        return String.format("%08d", (int) (Math.random() * 100_000_000));
    }

    public String getPaymentKey() { return paymentKey; }
    public String getOrderId() { return orderId; }
    public long getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getMethod() { return method; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public String getCancelReason() { return cancelReason; }
    public Card getCard() { return card; }

    public record Card(String issuerCode, String number, String approveNo) {}
}
