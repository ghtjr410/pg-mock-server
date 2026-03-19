# Mock 에러 트리거

Mock 서버에서 특정 에러를 의도적으로 발생시키려면 `orderId`(승인 시) 또는 `cancelReason`(취소 시)에 트리거 키워드를 포함시킵니다.

## 키워드 매칭 규칙

- **대소문자 무시**: `ALREADY_PROCESSED`나 `already_processed` 모두 매칭
- **부분 문자열 매칭**: `order_already_processed_001`처럼 키워드가 포함되면 매칭
- **우선순위**: `LinkedHashMap` 순서대로 첫 번째 매칭된 에러를 반환

## 결제 승인 에러 (orderId 키워드)

### 재시도 불가

| HTTP | code | message | 트리거 키워드 |
|------|------|---------|---------------|
| 400 | ALREADY_PROCESSED_PAYMENT | 이미 처리된 결제 입니다 | `already_processed` |
| 400 | INVALID_CARD_NUMBER | 카드번호를 다시 확인해주세요 | `invalid_card` |
| 400 | INVALID_STOPPED_CARD | 정지된 카드 입니다 | `stopped_card` |
| 400 | INVALID_CARD_EXPIRATION | 카드 정보를 다시 확인해주세요 (유효기간) | `expired_card` |
| 400 | INVALID_REJECT_CARD | 카드 사용이 거절되었습니다 | `reject_card` |
| 400 | EXCEED_MAX_AMOUNT | 거래금액 한도를 초과했습니다 | `exceed` |
| 400 | INVALID_CARD_LOST_OR_STOLEN | 분실 혹은 도난 카드입니다 | `lost_stolen` |
| 400 | UNAPPROVED_ORDER_ID | 아직 승인되지 않은 주문번호입니다 | `unapproved` |
| 403 | REJECT_CARD_PAYMENT | 한도초과 혹은 잔액부족 | `reject_payment` |
| 403 | REJECT_CARD_COMPANY | 결제 승인이 거절되었습니다 | `reject_company` |
| 403 | FORBIDDEN_REQUEST | 허용되지 않은 요청입니다 | `forbidden` |
| 404 | NOT_FOUND_PAYMENT_SESSION | 결제 시간이 만료됨 | `not_found_session` |

### 재시도 가능

| HTTP | code | message | 트리거 키워드 |
|------|------|---------|---------------|
| 400 | PROVIDER_ERROR | 일시적인 오류가 발생했습니다 | `provider_error` |
| 400 | CARD_PROCESSING_ERROR | 카드사에서 오류가 발생했습니다 | `card_processing` |
| 500 | FAILED_INTERNAL_SYSTEM_PROCESSING | 내부 시스템 처리 작업이 실패했습니다 | `system_error` |
| 500 | FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING | 결제가 완료되지 않았어요 | `payment_processing` |
| 500 | UNKNOWN_PAYMENT_ERROR | 결제에 실패했어요 | `unknown_error` |

## 결제 취소 에러 (cancelReason 키워드)

| HTTP | code | message | 트리거 키워드 | 재시도 |
|------|------|---------|---------------|--------|
| 403 | NOT_CANCELABLE_AMOUNT | 취소 할 수 없는 금액입니다 | `not_cancelable_amount` | X |
| 403 | NOT_CANCELABLE_PAYMENT | 취소 할 수 없는 결제입니다 | `not_cancelable` | X |
| 500 | FAILED_INTERNAL_SYSTEM_PROCESSING | 내부 시스템 처리 작업이 실패했습니다 | `cancel_system_error` | O |
| 500 | FAILED_METHOD_HANDLING_CANCEL | 결제수단 처리 오류입니다 | `cancel_method_error` | O |

## Mock 자체 검증 에러 (키워드 불필요)

### 인증 에러

| HTTP | code | message | 발생 조건 |
|------|------|---------|-----------|
| 401 | UNAUTHORIZED_KEY | 인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다. | Authorization 헤더 없음 또는 `Basic ` 접두사 없음 |
| 401 | UNAUTHORIZED_KEY | 시크릿 키 형식이 올바르지 않습니다. {secretKey}: 형식이어야 합니다. | Base64 디코딩 후 콜론(`:`)으로 끝나지 않음 |
| 401 | UNAUTHORIZED_KEY | Base64 디코딩에 실패했습니다. | Base64 디코딩 실패 |

### 승인 시

| HTTP | code | message | 발생 조건 |
|------|------|---------|-----------|
| 400 | INVALID_REQUEST | paymentKey, orderId, amount는 필수입니다. | 필수 파라미터 누락 또는 amount ≤ 0 |
| 400 | AMOUNT_MISMATCH | 결제 금액이 일치하지 않습니다. | 기존 결제의 amount와 불일치 |
| 400 | INVALID_REQUEST | 주문번호가 일치하지 않습니다. | 기존 결제의 orderId와 불일치 |

### 취소 시

| HTTP | code | message | 발생 조건 |
|------|------|---------|-----------|
| 404 | NOT_FOUND_PAYMENT | 존재하지 않는 결제 정보 입니다. | paymentKey에 해당하는 결제 없음 |
| 400 | ALREADY_CANCELED_PAYMENT | 이미 취소된 결제 입니다 | 결제 상태가 DONE/PARTIAL_CANCELED가 아님 |
| 403 | NOT_CANCELABLE_AMOUNT | 취소 할 수 없는 금액입니다 | cancelAmount > balanceAmount |

### 조회 시

| HTTP | code | message | 발생 조건 |
|------|------|---------|-----------|
| 404 | NOT_FOUND_PAYMENT | 존재하지 않는 결제 정보 입니다. | paymentKey/orderId에 해당하는 결제 없음 |

## 공식 스펙과의 메시지 차이

Mock 에러 메시지는 공식 스펙보다 축약되어 있습니다:

| code | Mock message | 공식 message |
|------|-------------|-------------|
| INVALID_REJECT_CARD | 카드 사용이 거절되었습니다 | 카드 사용이 거절되었습니다. 카드사 문의가 필요합니다. |
| REJECT_CARD_PAYMENT | 한도초과 혹은 잔액부족 | 한도초과 혹은 잔액부족으로 결제에 실패했습니다. |
| NOT_FOUND_PAYMENT_SESSION | 결제 시간이 만료됨 | 결제 시간이 만료되어 결제 진행 데이터가 존재하지 않습니다. |
| PROVIDER_ERROR | 일시적인 오류가 발생했습니다 | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요. |
| FAILED_INTERNAL_SYSTEM_PROCESSING | 내부 시스템 처리 작업이 실패했습니다 | 내부 시스템 처리 작업이 실패했습니다. 잠시 후 다시 시도해주세요. |
| FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING | 결제가 완료되지 않았어요 | 결제가 완료되지 않았어요. 다시 시도해주세요. |
| UNKNOWN_PAYMENT_ERROR | 결제에 실패했어요 | 결제에 실패했어요. 같은 문제가 반복된다면 은행이나 카드사로 문의해주세요. |
| FAILED_METHOD_HANDLING_CANCEL | 결제수단 처리 오류입니다 | 취소 중 결제 시 사용한 결제 수단 처리과정에서 일시적인 오류가 발생했습니다. |

## 사용 예시

```bash
# 승인 에러 트리거 (orderId에 키워드 포함)
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic dGVzdF9zZWNyZXRfa2V5Og==" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"pk_test","orderId":"order_already_processed_001","amount":10000}'
# → 400 {"code":"ALREADY_PROCESSED_PAYMENT","message":"이미 처리된 결제 입니다"}

# 취소 에러 트리거 (cancelReason에 키워드 포함)
curl -X POST http://localhost:8090/v1/payments/pk_test/cancel \
  -H "Authorization: Basic dGVzdF9zZWNyZXRfa2V5Og==" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason":"not_cancelable 테스트"}'
# → 403 {"code":"NOT_CANCELABLE_PAYMENT","message":"취소 할 수 없는 결제입니다"}
```
