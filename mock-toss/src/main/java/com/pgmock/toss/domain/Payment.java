package com.pgmock.toss.domain;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Payment {

    private static final DateTimeFormatter TOSS_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final String paymentKey;
    private final String orderId;
    private final String orderName;
    private final long totalAmount;
    private long balanceAmount;
    private final long suppliedAmount;
    private final long vat;
    private final long taxFreeAmount;
    private final long taxExemptionAmount;
    private String status;
    private final String method;
    private final String type;
    private final String mId;
    private final String currency;
    private final String country;
    private final String version;
    private final OffsetDateTime requestedAt;
    private OffsetDateTime approvedAt;
    private final boolean useEscrow;
    private final boolean cultureExpense;
    private boolean isPartialCancelable;
    private String lastTransactionKey;
    private final Card card;
    private final List<Cancel> cancels = new ArrayList<>();

    public Payment(String paymentKey, String orderId, long amount) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.orderName = "주문-" + orderId;
        this.totalAmount = amount;
        this.balanceAmount = amount;
        this.suppliedAmount = (long) Math.round(amount / 1.1);
        this.vat = amount - this.suppliedAmount;
        this.taxFreeAmount = 0;
        this.taxExemptionAmount = 0;
        this.status = "IN_PROGRESS";
        this.method = "카드";
        this.type = "NORMAL";
        this.mId = "tosspayments";
        this.currency = "KRW";
        this.country = "KR";
        this.version = "2022-11-16";
        this.requestedAt = OffsetDateTime.now(KST);
        this.useEscrow = false;
        this.cultureExpense = false;
        this.isPartialCancelable = true;
        this.lastTransactionKey = UUID.randomUUID().toString();
        this.card = new Card("11", "41", "1234-****-****-5678",
                generateApproveNo(), 0, false, null, "신용", "개인",
                "READY", amount);
    }

    public void approve() {
        this.status = "DONE";
        this.approvedAt = OffsetDateTime.now(KST);
        this.lastTransactionKey = UUID.randomUUID().toString();
    }

    public Cancel cancel(String reason, Long cancelAmount) {
        long actualCancelAmount = (cancelAmount != null) ? cancelAmount : this.balanceAmount;
        this.balanceAmount -= actualCancelAmount;

        if (this.balanceAmount == 0) {
            this.status = "CANCELED";
        } else {
            this.status = "PARTIAL_CANCELED";
        }
        this.isPartialCancelable = this.balanceAmount > 0;
        this.lastTransactionKey = UUID.randomUUID().toString();

        Cancel cancel = new Cancel(
                actualCancelAmount, reason, OffsetDateTime.now(KST),
                this.lastTransactionKey, this.balanceAmount
        );
        this.cancels.add(cancel);
        return cancel;
    }

    public boolean isDone() {
        return "DONE".equals(status);
    }

    public boolean isCancelable() {
        return "DONE".equals(status) || "PARTIAL_CANCELED".equals(status);
    }

    private String generateApproveNo() {
        return String.format("%08d", (int) (Math.random() * 100_000_000));
    }

    /** 토스 공식 형식으로 날짜 포맷 (나노초 제거, +09:00) */
    public static String formatDateTime(OffsetDateTime dt) {
        if (dt == null) return null;
        return dt.format(TOSS_DATE_FORMAT);
    }

    // getters
    public String getPaymentKey() { return paymentKey; }
    public String getOrderId() { return orderId; }
    public String getOrderName() { return orderName; }
    public long getTotalAmount() { return totalAmount; }
    public long getBalanceAmount() { return balanceAmount; }
    public long getSuppliedAmount() { return suppliedAmount; }
    public long getVat() { return vat; }
    public long getTaxFreeAmount() { return taxFreeAmount; }
    public long getTaxExemptionAmount() { return taxExemptionAmount; }
    public String getStatus() { return status; }
    public String getMethod() { return method; }
    public String getType() { return type; }
    public String getMId() { return mId; }
    public String getCurrency() { return currency; }
    public String getCountry() { return country; }
    public String getVersion() { return version; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public boolean isUseEscrow() { return useEscrow; }
    public boolean isCultureExpense() { return cultureExpense; }
    public boolean isPartialCancelable() { return isPartialCancelable; }
    public String getLastTransactionKey() { return lastTransactionKey; }
    public Card getCard() { return card; }
    public List<Cancel> getCancels() { return Collections.unmodifiableList(cancels); }

    public record Card(
            String issuerCode, String acquirerCode, String number, String approveNo,
            int installmentPlanMonths, boolean isInterestFree, String interestPayer,
            String cardType, String ownerType, String acquireStatus, long amount
    ) {}

    public record Cancel(
            long cancelAmount, String cancelReason, OffsetDateTime canceledAt,
            String transactionKey, long refundableAmount
    ) {}
}
