package com.pgmock.nice.domain;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Payment {

    private static final DateTimeFormatter NICE_DATE_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final String tid;
    private final String orderId;
    private final String goodsName;
    private final long amount;
    private long balanceAmt;
    private String status;
    private final String payMethod;
    private final String currency;
    private final String channel;
    private final boolean useEscrow;
    private final OffsetDateTime paidAt;
    private OffsetDateTime cancelledAt;
    private String approveNo;
    private final Card card;
    private final List<Cancel> cancels = new ArrayList<>();

    public Payment(String tid, long amount) {
        this.tid = tid;
        this.orderId = "ORDER-" + tid;
        this.goodsName = "상품-" + tid;
        this.amount = amount;
        this.balanceAmt = amount;
        this.status = "ready";
        this.payMethod = "card";
        this.currency = "KRW";
        this.channel = "pc";
        this.useEscrow = false;
        this.paidAt = OffsetDateTime.now(KST);
        this.approveNo = generateApproveNo();
        this.card = new Card("02", "KB국민", "536112******1234", "0",
                false, "credit", "true", "02", "KB국민");
    }

    public void approve() {
        this.status = "paid";
    }

    public Cancel cancel(String reason, Long cancelAmt) {
        long actualCancelAmt = (cancelAmt != null) ? cancelAmt : this.balanceAmt;
        this.balanceAmt -= actualCancelAmt;

        if (this.balanceAmt == 0) {
            this.status = "cancelled";
        } else {
            this.status = "partialCancelled";
        }
        this.cancelledAt = OffsetDateTime.now(KST);

        String cancelTid = tid + "-cancel-" + (cancels.size() + 1);
        Cancel cancel = new Cancel(cancelTid, actualCancelAmt, this.cancelledAt, reason);
        this.cancels.add(cancel);
        return cancel;
    }

    public boolean isPaid() {
        return "paid".equals(status);
    }

    public boolean isCancelable() {
        return "paid".equals(status) || "partialCancelled".equals(status);
    }

    private String generateApproveNo() {
        return String.format("%08d", (int) (Math.random() * 100_000_000));
    }

    public static String formatDateTime(OffsetDateTime dt) {
        if (dt == null) return "0";
        return dt.format(NICE_DATE_FORMAT);
    }

    // getters
    public String getTid() { return tid; }
    public String getOrderId() { return orderId; }
    public String getGoodsName() { return goodsName; }
    public long getAmount() { return amount; }
    public long getBalanceAmt() { return balanceAmt; }
    public String getStatus() { return status; }
    public String getPayMethod() { return payMethod; }
    public String getCurrency() { return currency; }
    public String getChannel() { return channel; }
    public boolean isUseEscrow() { return useEscrow; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public String getApproveNo() { return approveNo; }
    public Card getCard() { return card; }
    public List<Cancel> getCancels() { return Collections.unmodifiableList(cancels); }

    public record Card(
            String cardCode, String cardName, String cardNum, String cardQuota,
            boolean isInterestFree, String cardType, String canPartCancel,
            String acquCardCode, String acquCardName
    ) {}

    public record Cancel(
            String tid, long amount, OffsetDateTime cancelledAt, String reason
    ) {}
}
