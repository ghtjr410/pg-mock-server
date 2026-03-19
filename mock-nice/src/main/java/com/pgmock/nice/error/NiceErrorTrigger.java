package com.pgmock.nice.error;

import com.pgmock.nice.response.NicePaymentResponse;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NiceErrorTrigger {

    private NiceErrorTrigger() {}

    private record ErrorSpec(int httpStatus, String resultCode, String resultMsg) {}

    private static final Map<String, ErrorSpec> APPROVE_ERROR_TRIGGERS = new LinkedHashMap<>();
    private static final Map<String, ErrorSpec> CANCEL_ERROR_TRIGGERS = new LinkedHashMap<>();

    static {
        // === 승인 재시도 불가 ===
        APPROVE_ERROR_TRIGGERS.put("card_error", new ErrorSpec(400, "3011", "카드번호 오류"));
        APPROVE_ERROR_TRIGGERS.put("card_reject", new ErrorSpec(400, "3095", "카드사 실패 응답"));
        APPROVE_ERROR_TRIGGERS.put("amount_error", new ErrorSpec(400, "3041", "금액 오류(1000원 미만 신용카드 승인 불가)"));
        APPROVE_ERROR_TRIGGERS.put("duplicate_order", new ErrorSpec(400, "A127", "주문번호 중복 오류"));
        APPROVE_ERROR_TRIGGERS.put("expired_session", new ErrorSpec(400, "A245", "인증 시간이 초과 되었습니다"));
        APPROVE_ERROR_TRIGGERS.put("amount_mismatch", new ErrorSpec(400, "A123", "거래금액 불일치(인증된 금액과 승인요청 금액 불일치)"));
        APPROVE_ERROR_TRIGGERS.put("auth_fail", new ErrorSpec(401, "U104", "사용자 인증에 실패하였습니다"));
        // === 승인 재시도 가능 ===
        APPROVE_ERROR_TRIGGERS.put("provider_error", new ErrorSpec(500, "A110", "외부 연동결과 실패"));
        APPROVE_ERROR_TRIGGERS.put("system_error", new ErrorSpec(500, "9002", "Try-Catch-Exception"));
        APPROVE_ERROR_TRIGGERS.put("socket_error", new ErrorSpec(500, "U508", "서버로 소켓 연결 중 오류가 발생하였습니다"));

        // === 취소 에러 ===
        CANCEL_ERROR_TRIGGERS.put("cancel_fail", new ErrorSpec(400, "2003", "취소 실패"));
        CANCEL_ERROR_TRIGGERS.put("cancel_system_error", new ErrorSpec(500, "9002", "Try-Catch-Exception"));
    }

    public static ResponseEntity<?> checkApproveTrigger(String orderId) {
        return checkErrorTrigger(orderId, APPROVE_ERROR_TRIGGERS);
    }

    public static ResponseEntity<?> checkCancelTrigger(String reason) {
        return checkErrorTrigger(reason, CANCEL_ERROR_TRIGGERS);
    }

    private static ResponseEntity<?> checkErrorTrigger(String value, Map<String, ErrorSpec> triggers) {
        if (value == null) return null;
        String lower = value.toLowerCase();
        for (var entry : triggers.entrySet()) {
            if (lower.contains(entry.getKey())) {
                ErrorSpec spec = entry.getValue();
                return ResponseEntity.status(spec.httpStatus())
                        .body(NicePaymentResponse.errorBody(spec.resultCode(), spec.resultMsg()));
            }
        }
        return null;
    }
}
