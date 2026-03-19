# mock-toss API 가이드

mock-toss는 토스페이먼츠 결제 API의 Mock 서버입니다.
실무 코드의 `base-url`만 `http://localhost:8090`으로 변경하면, 코드 수정 없이 장애 대응 테스트가 가능합니다.

---

## 목차

1. [시작하기](#1-시작하기)
2. [인증](#2-인증)
3. [결제 승인 API](#3-결제-승인-api)
4. [결제 조회 API](#4-결제-조회-api)
5. [결제 취소 API](#5-결제-취소-api)
6. [멱등키 (Idempotency-Key)](#6-멱등키-idempotency-key)
7. [에러 트리거](#7-에러-트리거)
8. [카오스 모드](#8-카오스-모드)
9. [테스트 시나리오 레시피](#9-테스트-시나리오-레시피)

---

## 1. 시작하기

### 서버 실행

```bash
./gradlew :mock-toss:bootRun
# → http://localhost:8090
```

### 실무 프로젝트 연결

```yaml
# application-local.yml
payment:
  toss:
    base-url: http://localhost:8090   # mock-toss
    secret-key: test_sk_xxxx         # 아무 값이나 OK
```

변경 사항: **없음**. 프로필만 전환하면 됩니다.

---

## 2. 인증

모든 API에 `Authorization` 헤더가 필요합니다.
형식: `Basic {시크릿키 + ":" 를 Base64 인코딩}`

```bash
# 시크릿 키가 test_sk_xxxx 인 경우
AUTH=$(echo -n 'test_sk_xxxx:' | base64)
# → dGVzdF9za194eHh4Og==

curl -H "Authorization: Basic $AUTH" ...
```

> **Mock 특징**: 시크릿 키 값은 아무 문자열이나 사용 가능합니다. `콜론(:)`으로 끝나는 형식만 검증합니다.

### 인증 실패 응답

| 상황 | HTTP | 응답 |
|------|------|------|
| Authorization 헤더 없음 | 401 | `{"code":"UNAUTHORIZED_KEY","message":"인증되지 않은 시크릿 키 ..."}` |
| `Basic ` 접두사 없음 | 401 | 동일 |
| Base64 디코딩 실패 | 401 | `{"code":"UNAUTHORIZED_KEY","message":"Base64 디코딩에 실패했습니다."}` |
| 콜론(`:`)으로 안 끝남 | 401 | `{"code":"UNAUTHORIZED_KEY","message":"시크릿 키 형식이 올바르지 않습니다. ..."}` |

---

## 3. 결제 승인 API

실제 토스페이먼츠에서는 결제창 → 인증 → redirect 후 서버에서 승인을 호출합니다.
Mock에서는 결제창 과정을 생략하고, 바로 승인 API를 호출하면 결제가 생성+승인됩니다.

### 요청

```
POST /v1/payments/confirm
```

```bash
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentKey": "tpk_abc123",
    "orderId": "ORDER-001",
    "amount": 50000
  }'
```

| 파라미터 | 필수 | 타입 | 설명 |
|---------|------|------|------|
| `paymentKey` | O | string | 결제 고유 키 |
| `orderId` | O | string | 주문 ID |
| `amount` | O | number | 결제 금액 (1 이상) |

### 성공 응답 (200)

```json
{
  "version": "2024-06-01",
  "paymentKey": "tpk_abc123",
  "type": "NORMAL",
  "orderId": "ORDER-001",
  "orderName": "주문-ORDER-001",
  "mId": "tosspayments",
  "currency": "KRW",
  "method": "카드",
  "totalAmount": 50000,
  "balanceAmount": 50000,
  "suppliedAmount": 45455,
  "vat": 4545,
  "status": "DONE",
  "requestedAt": "2026-03-19T14:30:00+09:00",
  "approvedAt": "2026-03-19T14:30:00+09:00",
  "isPartialCancelable": true,
  "card": {
    "issuerCode": "11",
    "acquirerCode": "41",
    "number": "1234-****-****-5678",
    "installmentPlanMonths": 0,
    "isInterestFree": false,
    "approveNo": "12345678",
    "cardType": "신용",
    "ownerType": "개인",
    "acquireStatus": "READY",
    "amount": 50000
  },
  "cancels": null,
  "receipt": { "url": "https://mock-receipt.tosspayments.com/tpk_abc123" },
  "checkout": { "url": "https://mock-checkout.tosspayments.com/tpk_abc123" }
}
```

### 에러 응답

| HTTP | code | 발생 조건 |
|------|------|-----------|
| 400 | `INVALID_REQUEST` | 필수 파라미터 누락, amount ≤ 0 |
| 400 | `AMOUNT_MISMATCH` | 동일 paymentKey로 다른 금액 재요청 |
| 400 | `INVALID_REQUEST` | 동일 paymentKey로 다른 orderId 재요청 |

---

## 4. 결제 조회 API

### paymentKey로 조회

```
GET /v1/payments/{paymentKey}
```

```bash
curl http://localhost:8090/v1/payments/tpk_abc123 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"
```

### orderId로 조회

```
GET /v1/payments/orders/{orderId}
```

```bash
curl http://localhost:8090/v1/payments/orders/ORDER-001 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"
```

### 에러 응답

| HTTP | code | 발생 조건 |
|------|------|-----------|
| 404 | `NOT_FOUND_PAYMENT` | 해당 결제 없음 |

> **카오스 모드 참고**: GET 요청은 기본적으로 카오스 모드의 영향을 받지 않습니다.
> 이는 실무 시나리오를 반영합니다 — 승인이 타임아웃 나면 조회로 결제 상태를 확인해야 하기 때문입니다.

---

## 5. 결제 취소 API

### 요청

```
POST /v1/payments/{paymentKey}/cancel
```

#### 전액 취소

```bash
curl -X POST http://localhost:8090/v1/payments/tpk_abc123/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "cancelReason": "고객 변심"
  }'
```

#### 부분 취소

```bash
curl -X POST http://localhost:8090/v1/payments/tpk_abc123/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "cancelReason": "부분 환불",
    "cancelAmount": 20000
  }'
```

| 파라미터 | 필수 | 타입 | 설명 |
|---------|------|------|------|
| `cancelReason` | O | string | 취소 사유 |
| `cancelAmount` | - | number | 취소 금액. 없으면 전액 취소 |

> **공식 스펙과의 차이**: `refundReceiveAccount`(가상계좌 환불 계좌), `taxFreeAmount`(면세 금액), `currency` 파라미터는 Mock에서 지원하지 않습니다. 카드 결제 테스트에는 불필요합니다.

### 성공 응답 (200)

```json
{
  "status": "CANCELED",
  "totalAmount": 50000,
  "balanceAmount": 0,
  "suppliedAmount": 0,
  "vat": 0,
  "isPartialCancelable": false,
  "cancels": [
    {
      "cancelAmount": 50000,
      "cancelReason": "고객 변심",
      "taxFreeAmount": 0,
      "refundableAmount": 0,
      "canceledAt": "2026-03-19T14:35:00+09:00",
      "transactionKey": "...",
      "cancelStatus": "DONE",
      "cancelRequestId": null
    }
  ]
}
```

### 상태 변화

| 취소 유형 | status | balanceAmount | isPartialCancelable |
|-----------|--------|---------------|---------------------|
| 부분 취소 | `PARTIAL_CANCELED` | 잔액 > 0 | `true` |
| 전액 취소 | `CANCELED` | `0` | `false` |
| 부분 취소 여러 번 → 잔액 0 | `CANCELED` | `0` | `false` |

### 부분 취소 여러 번

```bash
# 1만원 결제 후, 3000 + 3000 + 4000 부분취소
# → cancels 배열에 3건 누적, 각각 transactionKey로 구분
```

```json
{
  "status": "CANCELED",
  "totalAmount": 10000,
  "balanceAmount": 0,
  "cancels": [
    { "cancelAmount": 3000, "refundableAmount": 7000, "transactionKey": "tx-1" },
    { "cancelAmount": 3000, "refundableAmount": 4000, "transactionKey": "tx-2" },
    { "cancelAmount": 4000, "refundableAmount": 0,    "transactionKey": "tx-3" }
  ]
}
```

### 에러 응답

| HTTP | code | 발생 조건 |
|------|------|-----------|
| 404 | `NOT_FOUND_PAYMENT` | paymentKey에 해당하는 결제 없음 |
| 400 | `ALREADY_CANCELED_PAYMENT` | 이미 전액 취소된 결제 |
| 403 | `NOT_CANCELABLE_AMOUNT` | cancelAmount > 남은 잔액 |

---

## 6. 멱등키 (Idempotency-Key)

승인과 취소 API에 `Idempotency-Key` 헤더를 추가하면, 동일한 키로 재요청 시 이전 응답을 그대로 반환합니다.
네트워크 타임아웃 후 재시도 시 **중복 결제/중복 취소를 방지**합니다.

```bash
# 승인 + 멱등키
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Idempotency-Key: idem-confirm-001" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_idem","orderId":"ORDER-IDEM","amount":10000}'

# 같은 멱등키로 재요청 → 동일 응답, 중복 처리 안 됨
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Idempotency-Key: idem-confirm-001" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_idem","orderId":"ORDER-IDEM","amount":10000}'
```

```bash
# 취소 + 멱등키
curl -X POST http://localhost:8090/v1/payments/tpk_idem/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Idempotency-Key: idem-cancel-001" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason":"환불","cancelAmount":5000}'

# 같은 멱등키로 재요청 → 동일 응답, 중복 취소 안 됨
```

---

## 7. 에러 트리거

카오스 모드와 별개로, **특정 에러를 확정적으로 발생시키는 방법**입니다.

- 승인: `orderId`에 키워드 포함
- 취소: `cancelReason`에 키워드 포함
- 대소문자 무시, 부분 문자열 매칭

### 승인 에러 트리거

#### 재시도 불가 (클라이언트가 재시도하면 안 되는 에러)

```bash
# 카드사 거절
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"pk_1","orderId":"order-reject_company-001","amount":5000}'
# → 403 {"code":"REJECT_CARD_COMPANY","message":"결제 승인이 거절되었습니다"}
```

| 키워드 | HTTP | code | 설명 |
|--------|------|------|------|
| `already_processed` | 400 | `ALREADY_PROCESSED_PAYMENT` | 이미 처리된 결제 |
| `invalid_card` | 400 | `INVALID_CARD_NUMBER` | 카드번호 오류 |
| `stopped_card` | 400 | `INVALID_STOPPED_CARD` | 정지된 카드 |
| `expired_card` | 400 | `INVALID_CARD_EXPIRATION` | 유효기간 오류 |
| `reject_card` | 400 | `INVALID_REJECT_CARD` | 카드 거절 |
| `exceed` | 400 | `EXCEED_MAX_AMOUNT` | 한도 초과 |
| `lost_stolen` | 400 | `INVALID_CARD_LOST_OR_STOLEN` | 분실/도난 카드 |
| `unapproved` | 400 | `UNAPPROVED_ORDER_ID` | 미승인 주문 |
| `reject_payment` | 403 | `REJECT_CARD_PAYMENT` | 한도초과/잔액부족 |
| `reject_company` | 403 | `REJECT_CARD_COMPANY` | 카드사 승인 거절 |
| `forbidden` | 403 | `FORBIDDEN_REQUEST` | 허용되지 않은 요청 |
| `not_found_session` | 404 | `NOT_FOUND_PAYMENT_SESSION` | 결제 시간 만료 |

#### 재시도 가능 (일시적 오류, 재시도로 성공할 수 있는 에러)

| 키워드 | HTTP | code | 설명 |
|--------|------|------|------|
| `provider_error` | 400 | `PROVIDER_ERROR` | 일시적 오류 |
| `card_processing` | 400 | `CARD_PROCESSING_ERROR` | 카드사 오류 |
| `system_error` | 500 | `FAILED_INTERNAL_SYSTEM_PROCESSING` | 내부 시스템 오류 |
| `payment_processing` | 500 | `FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING` | 결제 미완료 |
| `unknown_error` | 500 | `UNKNOWN_PAYMENT_ERROR` | 알 수 없는 오류 |

### 취소 에러 트리거

```bash
# 취소 불가 에러
curl -X POST http://localhost:8090/v1/payments/tpk_001/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason":"not_cancelable 테스트"}'
# → 403 {"code":"NOT_CANCELABLE_PAYMENT","message":"취소 할 수 없는 결제입니다"}
```

| 키워드 | HTTP | code | 설명 |
|--------|------|------|------|
| `not_cancelable_amount` | 403 | `NOT_CANCELABLE_AMOUNT` | 취소 불가 금액 |
| `not_cancelable` | 403 | `NOT_CANCELABLE_PAYMENT` | 취소 불가 결제 |
| `cancel_system_error` | 500 | `FAILED_INTERNAL_SYSTEM_PROCESSING` | 내부 시스템 오류 |
| `cancel_method_error` | 500 | `FAILED_METHOD_HANDLING_CANCEL` | 결제수단 처리 오류 |

> **주의**: `not_cancelable_amount`가 `not_cancelable`보다 먼저 매칭됩니다. 키워드 매칭은 등록 순서대로 첫 번째 매칭에서 결정됩니다.

---

## 8. 카오스 모드

카오스 모드는 **PG 서버 장애 상황을 시뮬레이션**합니다.
모든 POST/PUT 요청에 적용되며, GET(조회) 요청은 기본적으로 영향받지 않습니다.

### 모드 종류

#### NORMAL — 정상

기본 상태. 모든 요청이 정상 처리됩니다.

```bash
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"
```

#### SLOW — 지연 응답

요청마다 설정된 범위 내에서 **랜덤 지연** 후 정상 응답합니다.

```bash
# 기본: 3~10초 랜덤 지연
curl -X PUT "http://localhost:8090/chaos/mode?mode=SLOW"

# 커스텀: 5~15초 지연
curl -X PUT "http://localhost:8090/chaos/mode?mode=SLOW&slowMinMs=5000&slowMaxMs=15000"

# 극단적: 25~30초 (보통 readTimeout=30초이므로 거의 타임아웃)
curl -X PUT "http://localhost:8090/chaos/mode?mode=SLOW&slowMinMs=25000&slowMaxMs=30000"
```

| 파라미터 | 기본값 | 설명 |
|---------|--------|------|
| `slowMinMs` | 3000 | 최소 지연 시간 (ms) |
| `slowMaxMs` | 10000 | 최대 지연 시간 (ms) |

**테스트 대상:**
- Resilience4j `TimeLimiter` (readTimeout 초과 시 TimeoutException)
- HTTP 클라이언트 readTimeout 설정
- 응답 지연에 대한 사용자 경험 (로딩 UI)

#### TIMEOUT — 무응답

요청을 받은 후 **5분 동안 응답하지 않습니다.** 클라이언트의 readTimeout이 먼저 발동합니다.

```bash
curl -X PUT "http://localhost:8090/chaos/mode?mode=TIMEOUT"
```

**테스트 대상:**
- 클라이언트 readTimeout이 정상 동작하는지
- 타임아웃 후 조회 API로 결제 상태를 확인하는 로직
- 타임아웃 후 재시도 + 멱등키 조합

**핵심 시나리오: 승인 타임아웃 후 결제 상태 확인**
```bash
# 1) 타임아웃 모드 설정
curl -X PUT "http://localhost:8090/chaos/mode?mode=TIMEOUT"

# 2) confirm 호출 → 클라이언트 타임아웃 발생
#    (Mock에서는 실제로 결제가 승인되지 않음 — 인터셉터에서 차단)

# 3) GET 조회는 카오스 영향 안 받음 → 결제 상태 확인 가능
curl http://localhost:8090/v1/payments/tpk_001 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"

# 4) 복구 후 재시도
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"
```

#### DEAD — 즉시 500 에러

모든 요청에 즉시 `500 Internal Server Error`를 반환합니다.

```bash
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD"
```

응답:
```json
{
  "code": "FAILED_INTERNAL_SYSTEM_PROCESSING",
  "message": "내부 시스템 처리 작업이 실패했습니다"
}
```

**테스트 대상:**
- Resilience4j `CircuitBreaker` — 연속 실패 시 OPEN 전이
- Fallback 로직 (다른 PG 전환, 사용자 안내 등)
- 장애 복구 후 HALF_OPEN → CLOSED 전이

**핵심 시나리오: 서킷브레이커 OPEN → 복구**
```bash
# 1) 장애 시작
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD"

# 2) 결제 승인 N건 호출 → 모두 500
#    → slidingWindow 실패율 100% → 서킷 OPEN
#    → 이후 요청은 서킷에서 바로 차단 (CallNotPermittedException)

# 3) 장애 복구
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"

# 4) waitDurationInOpenState 경과 후
#    → HALF_OPEN 상태로 전이
#    → permittedNumberOfCallsInHalfOpenState 만큼 요청 통과
#    → 성공하면 CLOSED 복귀
```

#### PARTIAL_FAILURE — 확률적 실패

설정된 확률로 **일부 요청만 실패**합니다. 나머지는 정상 응답합니다.

```bash
# 50% 확률 실패 (기본값)
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE"

# 30% 확률 실패
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=30"

# 80% 확률 실패 (거의 장애 수준)
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=80"
```

| 파라미터 | 기본값 | 설명 |
|---------|--------|------|
| `partialFailureRate` | 50 | 실패 확률 (0~100) |

**실패 시 에러 분포:**

| 비율 | 유형 | 에러 풀 |
|------|------|---------|
| 70% | 재시도 가능 | `500 FAILED_INTERNAL_SYSTEM_PROCESSING`, `400 PROVIDER_ERROR`, `500 UNKNOWN_PAYMENT_ERROR` |
| 30% | 재시도 불가 | `403 REJECT_CARD_COMPANY`, `403 REJECT_CARD_PAYMENT`, `403 FORBIDDEN_REQUEST` |

**테스트 대상:**
- Resilience4j `CircuitBreaker`의 `slidingWindow` 기반 실패율 계산
- `failureRateThreshold` 도달 시 OPEN 전이
- 재시도(Retry) 정책 — 재시도 가능 에러만 재시도
- 재시도 불가 에러를 재시도하지 않는지 확인

**핵심 시나리오: slidingWindow 실패율 테스트**
```bash
# slidingWindowSize=10, failureRateThreshold=50 설정일 때

# 1) 30% 실패 → 서킷 CLOSED 유지
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=30"
# → 10건 중 ~3건 실패 → 실패율 30% → threshold 미달 → CLOSED

# 2) 70% 실패 → 서킷 OPEN
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=70"
# → 10건 중 ~7건 실패 → 실패율 70% → threshold 초과 → OPEN

# 3) 정상 복구
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"
```

### 카오스 모드 조회

현재 설정을 확인합니다.

```bash
curl http://localhost:8090/chaos/mode
```

```json
{
  "mode": "PARTIAL_FAILURE",
  "slowMinMs": 3000,
  "slowMaxMs": 10000,
  "partialFailureRate": 50,
  "affectReadApis": false
}
```

### GET 요청에도 카오스 적용

기본적으로 GET(조회) 요청은 카오스 모드의 영향을 받지 않습니다.
조회 API에도 장애를 주입하려면:

```bash
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD&affectReadApis=true"
```

**사용 시나리오:**
- 승인 타임아웃 후 조회도 실패하는 경우 테스트
- PG 전체 장애 (승인 + 조회 모두 불가) 시뮬레이션

### 요청별 카오스 오버라이드

글로벌 설정과 관계없이, `X-CHAOS-MODE` 헤더로 개별 요청의 카오스 모드를 지정할 수 있습니다.

```bash
# 글로벌은 NORMAL이지만, 이 요청만 DEAD
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "X-CHAOS-MODE: DEAD" \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_x","orderId":"ORDER-X","amount":10000}'
# → 500

# 글로벌은 DEAD이지만, 이 요청만 NORMAL
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "X-CHAOS-MODE: NORMAL" \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_y","orderId":"ORDER-Y","amount":10000}'
# → 200 정상
```

**사용 시나리오:**
- 통합 테스트에서 특정 요청만 실패시키고 싶을 때
- 글로벌 모드를 변경하지 않고 개별 테스트 케이스 작성

---

## 9. 테스트 시나리오 레시피

### 시나리오 1: 타임아웃 → 조회 → 멱등키 재시도

결제 승인 중 타임아웃 발생 시 안전하게 복구하는 플로우.

```bash
# 1) 타임아웃 모드 설정
curl -X PUT "http://localhost:8090/chaos/mode?mode=TIMEOUT"

# 2) 결제 승인 (멱등키 포함) → 클라이언트 타임아웃 발생
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Idempotency-Key: idem-001" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_timeout","orderId":"ORDER-T","amount":30000}'
# → 타임아웃 (응답 못 받음)

# 3) 조회로 상태 확인 (GET은 카오스 영향 없음)
curl http://localhost:8090/v1/payments/tpk_timeout \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"
# → 404 (타임아웃 모드에서는 인터셉터에서 차단되어 결제 생성 안 됨)

# 4) 복구 후 동일 멱등키로 재시도
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"

curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Idempotency-Key: idem-001" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_timeout","orderId":"ORDER-T","amount":30000}'
# → 200 정상 승인
```

### 시나리오 2: 서킷브레이커 전이 (CLOSED → OPEN → HALF_OPEN → CLOSED)

```bash
# 1) 정상 상태에서 결제 몇 건 성공
# → 서킷 CLOSED

# 2) DEAD 모드로 장애 발생
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD"

# 3) 결제 승인 연속 호출 → 모두 500
# → slidingWindow 내 실패율 100% → 서킷 OPEN
# → fallback 실행 (예: "일시적 오류, 다시 시도해주세요")

# 4) 장애 복구
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"

# 5) waitDurationInOpenState 경과 → HALF_OPEN
# → 제한된 횟수만 요청 통과 → 성공 → CLOSED 복귀
```

### 시나리오 3: 재시도 정책 검증

에러 코드에 따라 재시도 여부를 구분하는 로직 테스트.

```bash
# 재시도 가능 에러 — orderId에 키워드 포함
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"pk_r1","orderId":"order-provider_error-001","amount":5000}'
# → 400 PROVIDER_ERROR (재시도 해야 함)

# 재시도 불가 에러 — 재시도하면 안 됨
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"pk_r2","orderId":"order-reject_company-001","amount":5000}'
# → 403 REJECT_CARD_COMPANY (재시도하면 안 됨)
```

### 시나리오 4: 결제 승인 후 취소 장애

승인은 성공했지만 취소 시 PG 장애가 발생하는 경우.

```bash
# 1) 정상 모드에서 결제 승인
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_cancel_fail","orderId":"ORDER-CF","amount":20000}'

# 2) DEAD 모드로 전환
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD"

# 3) 취소 시도 → 500 에러
curl -X POST http://localhost:8090/v1/payments/tpk_cancel_fail/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason":"환불 요청"}'
# → 500 (카오스 인터셉터에서 차단)

# 4) 복구 후 재시도
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"

curl -X POST http://localhost:8090/v1/payments/tpk_cancel_fail/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Idempotency-Key: cancel-retry-001" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason":"환불 요청"}'
# → 200 취소 성공
```

### 시나리오 5: 간헐적 장애에서 부분 취소 안전성

```bash
# 1) 결제 승인
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_partial_chaos","orderId":"ORDER-PC","amount":30000}'

# 2) 50% 확률 장애
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=50"

# 3) 부분 취소 (멱등키 필수!) → 실패 시 같은 멱등키로 재시도
curl -X POST http://localhost:8090/v1/payments/tpk_partial_chaos/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Idempotency-Key: partial-cancel-001" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason":"부분 환불","cancelAmount":10000}'
# → 성공할 때까지 같은 멱등키로 재시도
# → 멱등키 덕분에 중복 취소 없이 안전

# 4) 복구
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"
```

---

## 카오스 모드 vs 에러 트리거 비교

| | 카오스 모드 | 에러 트리거 |
|---|---|---|
| **대상** | 모든 요청 (글로벌) | 특정 요청 (키워드 매칭) |
| **확정성** | DEAD: 확정 실패, PARTIAL_FAILURE: 확률적 | 항상 확정적 |
| **에러 종류** | 고정 (500, PARTIAL_FAILURE 풀) | 토스 공식 에러 코드 전체 |
| **설정 방법** | `PUT /chaos/mode` 또는 `X-CHAOS-MODE` 헤더 | orderId / cancelReason에 키워드 |
| **용도** | Resilience4j 서킷/타임아웃/재시도 테스트 | 비즈니스 에러 핸들링 테스트 |
| **조합** | 카오스 + 에러 트리거 동시 사용 가능 | 카오스가 먼저 적용됨 |

> **적용 순서**: 카오스 인터셉터(글로벌) → 에러 트리거(서비스 레이어). 카오스 모드가 DEAD면 에러 트리거까지 도달하지 않습니다.
