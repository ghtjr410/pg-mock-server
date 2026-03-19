package com.pgmock.toss.error;

import com.pgmock.toss.response.TossPaymentResponse;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TossErrorTrigger {

    private TossErrorTrigger() {}

    private record ErrorSpec(int httpStatus, String code, String message) {}

    private static final Map<String, ErrorSpec> CONFIRM_ERROR_TRIGGERS = new LinkedHashMap<>();
    private static final Map<String, ErrorSpec> CANCEL_ERROR_TRIGGERS = new LinkedHashMap<>();

    static {
        // === confirm 재시도 불가 ===
        CONFIRM_ERROR_TRIGGERS.put("already_processed", new ErrorSpec(400, "ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다"));
        CONFIRM_ERROR_TRIGGERS.put("invalid_card", new ErrorSpec(400, "INVALID_CARD_NUMBER", "카드번호를 다시 확인해주세요"));
        CONFIRM_ERROR_TRIGGERS.put("stopped_card", new ErrorSpec(400, "INVALID_STOPPED_CARD", "정지된 카드 입니다"));
        CONFIRM_ERROR_TRIGGERS.put("expired_card", new ErrorSpec(400, "INVALID_CARD_EXPIRATION", "카드 정보를 다시 확인해주세요 (유효기간)"));
        CONFIRM_ERROR_TRIGGERS.put("reject_card", new ErrorSpec(400, "INVALID_REJECT_CARD", "카드 사용이 거절되었습니다"));
        CONFIRM_ERROR_TRIGGERS.put("exceed", new ErrorSpec(400, "EXCEED_MAX_AMOUNT", "거래금액 한도를 초과했습니다"));
        CONFIRM_ERROR_TRIGGERS.put("lost_stolen", new ErrorSpec(400, "INVALID_CARD_LOST_OR_STOLEN", "분실 혹은 도난 카드입니다"));
        CONFIRM_ERROR_TRIGGERS.put("unapproved", new ErrorSpec(400, "UNAPPROVED_ORDER_ID", "아직 승인되지 않은 주문번호입니다"));
        CONFIRM_ERROR_TRIGGERS.put("reject_payment", new ErrorSpec(403, "REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족"));
        CONFIRM_ERROR_TRIGGERS.put("reject_company", new ErrorSpec(403, "REJECT_CARD_COMPANY", "결제 승인이 거절되었습니다"));
        CONFIRM_ERROR_TRIGGERS.put("forbidden", new ErrorSpec(403, "FORBIDDEN_REQUEST", "허용되지 않은 요청입니다"));
        CONFIRM_ERROR_TRIGGERS.put("not_found_session", new ErrorSpec(404, "NOT_FOUND_PAYMENT_SESSION", "결제 시간이 만료됨"));
        // === confirm 재시도 가능 ===
        CONFIRM_ERROR_TRIGGERS.put("provider_error", new ErrorSpec(400, "PROVIDER_ERROR", "일시적인 오류가 발생했습니다"));
        CONFIRM_ERROR_TRIGGERS.put("card_processing", new ErrorSpec(400, "CARD_PROCESSING_ERROR", "카드사에서 오류가 발생했습니다"));
        CONFIRM_ERROR_TRIGGERS.put("system_error", new ErrorSpec(500, "FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다"));
        CONFIRM_ERROR_TRIGGERS.put("payment_processing", new ErrorSpec(500, "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING", "결제가 완료되지 않았어요"));
        CONFIRM_ERROR_TRIGGERS.put("unknown_error", new ErrorSpec(500, "UNKNOWN_PAYMENT_ERROR", "결제에 실패했어요"));

        // === cancel 에러 ===
        CANCEL_ERROR_TRIGGERS.put("not_cancelable_amount", new ErrorSpec(403, "NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액입니다"));
        CANCEL_ERROR_TRIGGERS.put("not_cancelable", new ErrorSpec(403, "NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제입니다"));
        CANCEL_ERROR_TRIGGERS.put("cancel_system_error", new ErrorSpec(500, "FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 처리 작업이 실패했습니다"));
        CANCEL_ERROR_TRIGGERS.put("cancel_method_error", new ErrorSpec(500, "FAILED_METHOD_HANDLING_CANCEL", "결제수단 처리 오류입니다"));
    }

    public static ResponseEntity<?> checkConfirmTrigger(String orderId) {
        return checkErrorTrigger(orderId, CONFIRM_ERROR_TRIGGERS);
    }

    public static ResponseEntity<?> checkCancelTrigger(String cancelReason) {
        return checkErrorTrigger(cancelReason, CANCEL_ERROR_TRIGGERS);
    }

    private static ResponseEntity<?> checkErrorTrigger(String value, Map<String, ErrorSpec> triggers) {
        if (value == null) return null;
        String lower = value.toLowerCase();
        for (var entry : triggers.entrySet()) {
            if (lower.contains(entry.getKey())) {
                ErrorSpec spec = entry.getValue();
                return ResponseEntity.status(spec.httpStatus())
                        .body(TossPaymentResponse.errorBody(spec.code(), spec.message()));
            }
        }
        return null;
    }
}
