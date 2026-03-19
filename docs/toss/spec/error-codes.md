# 토스페이먼츠 에러 코드

> 공식 문서: https://docs.tosspayments.com/reference/error-codes

## 에러 응답 형식

```json
{
  "code": "ERROR_CODE",
  "message": "에러 메시지"
}
```

> 2026-03-17 기준 Playwright로 공식 문서에서 추출

## 결제 승인 에러

| HTTP | code | message |
|------|------|---------|
| 400 | ALREADY_PROCESSED_PAYMENT | 이미 처리된 결제 입니다. |
| 400 | INVALID_CARD_NUMBER | 카드번호를 다시 확인해주세요. |
| 400 | INVALID_STOPPED_CARD | 정지된 카드 입니다. |
| 400 | INVALID_CARD_EXPIRATION | 카드 정보를 다시 확인해주세요 (유효기간). |
| 400 | INVALID_REJECT_CARD | 카드 사용이 거절되었습니다. 카드사 문의가 필요합니다. |
| 400 | EXCEED_MAX_AMOUNT | 거래금액 한도를 초과했습니다. |
| 400 | INVALID_CARD_LOST_OR_STOLEN | 분실 혹은 도난 카드입니다. |
| 400 | UNAPPROVED_ORDER_ID | 아직 승인되지 않은 주문번호입니다. |
| 400 | INVALID_REQUEST | 잘못된 요청입니다. |
| 400 | EXCEED_MAX_CARD_INSTALLMENT_PLAN | 설정 가능한 최대 할부 개월 수를 초과했습니다. |
| 400 | NOT_ALLOWED_POINT_USE | 포인트 사용이 불가한 카드로 카드 포인트 결제에 실패했습니다. |
| 400 | INVALID_API_KEY | 잘못된 시크릿키 연동 정보 입니다. |
| 400 | BELOW_MINIMUM_AMOUNT | 신용카드는 결제금액이 100원 이상, 계좌는 200원이상부터 결제가 가능합니다. |
| 400 | EXCEED_MAX_DAILY_PAYMENT_COUNT | 하루 결제 가능 횟수를 초과했습니다. |
| 400 | NOT_SUPPORTED_INSTALLMENT_PLAN_CARD_OR_MERCHANT | 할부가 지원되지 않는 카드 또는 가맹점 입니다. |
| 400 | INVALID_CARD_INSTALLMENT_PLAN | 할부 개월 정보가 잘못되었습니다. |
| 400 | NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN | 할부가 지원되지 않는 카드입니다. |
| 400 | EXCEED_MAX_PAYMENT_AMOUNT | 하루 결제 가능 금액을 초과했습니다. |
| 400 | NOT_FOUND_TERMINAL_ID | 단말기번호(Terminal Id)가 없습니다. |
| 400 | INVALID_AUTHORIZE_AUTH | 유효하지 않은 인증 방식입니다. |
| 400 | RESTRICTED_TRANSFER_ACCOUNT | 계좌는 등록 후 12시간 뒤부터 결제할 수 있습니다. |
| 400 | INVALID_UNREGISTERED_SUBMALL | 등록되지 않은 서브몰입니다. |
| 400 | NOT_REGISTERED_BUSINESS | 등록되지 않은 사업자 번호입니다. |
| 400 | EXCEED_MAX_ONE_DAY_WITHDRAW_AMOUNT | 1일 출금 한도를 초과했습니다. |
| 400 | EXCEED_MAX_ONE_TIME_WITHDRAW_AMOUNT | 1회 출금 한도를 초과했습니다. |
| 400 | INVALID_ACCOUNT_INFO_RE_REGISTER | 유효하지 않은 계좌입니다. 계좌 재등록 후 시도해주세요. |
| 400 | NOT_AVAILABLE_PAYMENT | 결제가 불가능한 시간대입니다. |
| 400 | EXCEED_MAX_MONTHLY_PAYMENT_AMOUNT | 당월 결제 가능금액인 1,000,000원을 초과 하셨습니다. |
| 400 | PROVIDER_ERROR | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요. |
| 400 | CARD_PROCESSING_ERROR | 카드사에서 오류가 발생했습니다. |
| 403 | REJECT_CARD_PAYMENT | 한도초과 혹은 잔액부족으로 결제에 실패했습니다. |
| 403 | REJECT_CARD_COMPANY | 결제 승인이 거절되었습니다. |
| 403 | FORBIDDEN_REQUEST | 허용되지 않은 요청입니다. |
| 403 | REJECT_ACCOUNT_PAYMENT | 잔액부족으로 결제에 실패했습니다. |
| 403 | REJECT_TOSSPAY_INVALID_ACCOUNT | 선택하신 출금 계좌가 출금이체 등록이 되어 있지 않아요. |
| 403 | EXCEED_MAX_AUTH_COUNT | 최대 인증 횟수를 초과했습니다. |
| 403 | EXCEED_MAX_ONE_DAY_AMOUNT | 일일 한도를 초과했습니다. |
| 403 | NOT_AVAILABLE_BANK | 은행 서비스 시간이 아닙니다. |
| 403 | INVALID_PASSWORD | 결제 비밀번호가 일치하지 않습니다. |
| 403 | INCORRECT_BASIC_AUTH_FORMAT | 잘못된 요청입니다. ':' 를 포함해 인코딩해주세요. |
| 403 | FDS_ERROR | 위험거래가 감지되어 결제가 제한됩니다. |
| 404 | NOT_FOUND_PAYMENT | 존재하지 않는 결제 정보 입니다. |
| 404 | NOT_FOUND_PAYMENT_SESSION | 결제 시간이 만료되어 결제 진행 데이터가 존재하지 않습니다. |
| 500 | FAILED_INTERNAL_SYSTEM_PROCESSING | 내부 시스템 처리 작업이 실패했습니다. 잠시 후 다시 시도해주세요. |
| 500 | FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING | 결제가 완료되지 않았어요. 다시 시도해주세요. |
| 500 | UNKNOWN_PAYMENT_ERROR | 결제에 실패했어요. 같은 문제가 반복된다면 은행이나 카드사로 문의해주세요. |

## 결제 조회 에러

| HTTP | code | message |
|------|------|---------|
| 400 | NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN_BELOW_AMOUNT | 5만원 이하의 결제는 할부가 불가능해서 결제에 실패했습니다. |
| 403 | FORBIDDEN_CONSECUTIVE_REQUEST | 반복적인 요청은 허용되지 않습니다. 잠시 후 다시 시도해주세요. |
| 403 | INCORRECT_BASIC_AUTH_FORMAT | 잘못된 요청입니다. ':' 를 포함해 인코딩해주세요. |
| 404 | NOT_FOUND | 존재하지 않는 정보 입니다. |
| 500 | FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING | 결제가 완료되지 않았어요. 다시 시도해주세요. |

## 결제 취소 에러

| HTTP | code | message |
|------|------|---------|
| 400 | INVALID_REFUND_ACCOUNT_INFO | 환불 계좌번호와 예금주명이 일치하지 않습니다. |
| 400 | EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT | 즉시할인금액보다 적은 금액은 부분취소가 불가능합니다. |
| 400 | INVALID_REQUEST | 잘못된 요청입니다. |
| 400 | INVALID_REFUND_ACCOUNT_NUMBER | 잘못된 환불 계좌번호입니다. |
| 400 | INVALID_BANK | 유효하지 않은 은행입니다. |
| 400 | NOT_MATCHES_REFUNDABLE_AMOUNT | 잔액 결과가 일치하지 않습니다. |
| 400 | PROVIDER_ERROR | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요. |
| 400 | REFUND_REJECTED | 환불이 거절됐습니다. 결제사에 문의 부탁드립니다. |
| 400 | ALREADY_REFUND_PAYMENT | 이미 환불된 결제입니다. |
| 400 | FORBIDDEN_BANK_REFUND_REQUEST | 고객 계좌가 입금이 되지 않는 상태입니다. |
| 403 | NOT_CANCELABLE_AMOUNT | 취소 할 수 없는 금액 입니다. |
| 403 | NOT_CANCELABLE_PAYMENT | 취소 할 수 없는 결제 입니다. |
| 403 | FORBIDDEN_CONSECUTIVE_REQUEST | 반복적인 요청은 허용되지 않습니다. |
| 403 | FORBIDDEN_REQUEST | 허용되지 않은 요청입니다. |
| 403 | EXCEED_MAX_REFUND_DUE | 환불 가능한 기간이 지났습니다. |
| 403 | NOT_ALLOWED_PARTIAL_REFUND_WAITING_DEPOSIT | 입금 대기중인 결제는 부분 환불이 불가합니다. |
| 403 | NOT_ALLOWED_PARTIAL_REFUND | 에스크로 주문, 현금 카드 결제일 때는 부분 환불이 불가합니다. |
| 403 | NOT_AVAILABLE_BANK | 은행 서비스 시간이 아닙니다. |
| 403 | INCORRECT_BASIC_AUTH_FORMAT | 잘못된 요청입니다. ':' 를 포함해 인코딩해주세요. |
| 403 | NOT_CANCELABLE_PAYMENT_FOR_DORMANT_USER | 휴면 처리된 회원의 결제는 취소할 수 없습니다. |
| 403 | EXCEED_CANCEL_LIMIT | 취소 한도 금액을 초과 하였습니다. |
| 500 | FAILED_REFUND_PROCESS | 환불요청에 실패했습니다. |
| 500 | FAILED_PARTIAL_REFUND | 부분 환불이 실패했습니다. |
| 500 | FAILED_METHOD_HANDLING_CANCEL | 취소 중 결제 시 사용한 결제 수단 처리과정에서 일시적인 오류가 발생했습니다. |
| 500 | COMMON_ERROR | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요. |
| 500 | FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING | 결제가 완료되지 않았어요. 다시 시도해주세요. |
