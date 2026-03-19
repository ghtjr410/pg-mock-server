# Mock 에러 트리거

Mock 서버에서 특정 에러를 의도적으로 발생시키려면 승인 시 `tid`(→ `orderId = ORDER-{tid}`)에, 취소 시 `reason`에 트리거 키워드를 포함시킵니다.

## 키워드 매칭 규칙

- **대소문자 무시**: `CARD_ERROR`나 `card_error` 모두 매칭
- **부분 문자열 매칭**: `ORDER-card_error-001`처럼 키워드가 포함되면 매칭
- **우선순위**: `LinkedHashMap` 순서대로 첫 번째 매칭된 에러를 반환

## 결제 승인 에러 (orderId 키워드)

### 재시도 불가

| HTTP | resultCode | resultMsg | 트리거 키워드 |
|------|-----------|-----------|---------------|
| 400 | 3011 | 카드번호 오류 | `card_error` |
| 400 | 3095 | 카드사 실패 응답 | `card_reject` |
| 400 | 3041 | 금액 오류(1000원 미만 신용카드 승인 불가) | `amount_error` |
| 400 | A127 | 주문번호 중복 오류 | `duplicate_order` |
| 400 | A245 | 인증 시간이 초과 되었습니다 | `expired_session` |
| 400 | A123 | 거래금액 불일치 | `amount_mismatch` |
| 401 | U104 | 사용자 인증에 실패하였습니다 | `auth_fail` |

### 재시도 가능

| HTTP | resultCode | resultMsg | 트리거 키워드 |
|------|-----------|-----------|---------------|
| 500 | A110 | 외부 연동결과 실패 | `provider_error` |
| 500 | 9002 | Try-Catch-Exception | `system_error` |
| 500 | U508 | 서버로 소켓 연결 중 오류가 발생하였습니다 | `socket_error` |

## 결제 취소 에러 (reason 키워드)

| HTTP | resultCode | resultMsg | 트리거 키워드 | 재시도 |
|------|-----------|-----------|---------------|--------|
| 400 | 2003 | 취소 실패 | `cancel_fail` | X |
| 500 | 9002 | Try-Catch-Exception | `cancel_system_error` | O |

## Mock 자체 검증 에러 (키워드 불필요)

### 인증 에러

| HTTP | resultCode | resultMsg | 발생 조건 |
|------|-----------|-----------|-----------|
| 401 | U104 | 사용자 인증에 실패하였습니다. | Authorization 헤더 없음 또는 `Basic ` 접두사 없음 |
| 401 | U104 | clientKey:secretKey 형식이어야 합니다. | Base64 디코딩 후 콜론(`:`) 없거나 양쪽 값 없음 |
| 401 | U104 | Base64 디코딩에 실패했습니다. | Base64 디코딩 실패 |

### 승인 시

| HTTP | resultCode | resultMsg | 발생 조건 |
|------|-----------|-----------|-----------|
| 400 | 9000 | 필수 필드값이 누락되었습니다. | tid 비어있음 또는 amount ≤ 0 |
| 400 | A123 | 거래금액 불일치 | 기존 결제의 amount와 불일치 |

### 취소 시

| HTTP | resultCode | resultMsg | 발생 조건 |
|------|-----------|-----------|-----------|
| 400 | 9000 | reason/orderId 필드값이 누락되었습니다. | 필수 파라미터 누락 |
| 404 | 2012 | 취소 해당거래 없음 | tid에 해당하는 결제 없음 |
| 400 | 2013 | 취소 완료 거래 | 이미 전액 취소된 결제 |
| 403 | 2032 | 취소금액이 취소가능금액보다 큼 | cancelAmt > balanceAmt |

### 조회 시

| HTTP | resultCode | resultMsg | 발생 조건 |
|------|-----------|-----------|-----------|
| 404 | A118 | 조회 결과데이터 없음 | tid/orderId에 해당하는 결제 없음 |

## 사용 예시

```bash
# 승인 에러 트리거 (tid에 키워드 → orderId = ORDER-{tid})
curl -X POST http://localhost:8091/v1/payments/ORDER-card_error-001 \
  -H "Authorization: Basic dGVzdENsaWVudEtleTp0ZXN0U2VjcmV0S2V5" \
  -H "Content-Type: application/json" \
  -d '{"amount":10000}'
# → 400 {"resultCode":"3011","resultMsg":"카드번호 오류"}

# 취소 에러 트리거 (reason에 키워드)
curl -X POST http://localhost:8091/v1/payments/nicuntct_001/cancel \
  -H "Authorization: Basic dGVzdENsaWVudEtleTp0ZXN0U2VjcmV0S2V5" \
  -H "Content-Type: application/json" \
  -d '{"reason":"cancel_fail 테스트","orderId":"ORDER-nicuntct_001"}'
# → 400 {"resultCode":"2003","resultMsg":"취소 실패"}
```
