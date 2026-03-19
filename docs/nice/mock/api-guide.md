# mock-nice API 가이드

mock-nice는 나이스페이먼츠 결제 API의 Mock 서버입니다.
실무 코드의 `base-url`만 `http://localhost:8091`로 변경하면, 코드 수정 없이 장애 대응 테스트가 가능합니다.

---

## 목차

1. [시작하기](#1-시작하기)
2. [인증](#2-인증)
3. [결제 승인 API](#3-결제-승인-api)
4. [결제 조회 API](#4-결제-조회-api)
5. [결제 취소 API](#5-결제-취소-api)
6. [에러 트리거](#6-에러-트리거)
7. [카오스 모드](#7-카오스-모드)
8. [테스트 초기화 API](#8-테스트-초기화-api)
9. [테스트 시나리오 레시피](#9-테스트-시나리오-레시피)

---

## 1. 시작하기

### 서버 실행

```bash
./gradlew :mock-nice:bootRun
# → http://localhost:8091
```

### 실무 프로젝트 연결

```yaml
# application-local.yml
payment:
  nice:
    base-url: http://localhost:8091   # mock-nice
    client-key: testClientKey         # 아무 값이나 OK
    secret-key: testSecretKey         # 아무 값이나 OK
```

변경 사항: **없음**. 프로필만 전환하면 됩니다.

---

## 2. 인증

모든 API에 `Authorization` 헤더가 필요합니다.
형식: `Basic {clientKey + ":" + secretKey 를 Base64 인코딩}`

```bash
# clientKey가 testClientKey, secretKey가 testSecretKey 인 경우
AUTH=$(echo -n 'testClientKey:testSecretKey' | base64)
# → dGVzdENsaWVudEtleTp0ZXN0U2VjcmV0S2V5

curl -H "Authorization: Basic $AUTH" ...
```

> **Mock 특징**: 키 값은 아무 문자열이나 사용 가능합니다. `clientKey:secretKey` 형식(콜론 포함, 양쪽 값 있음)만 검증합니다.
> **토스와의 차이**: 토스는 `secretKey:` (콜론 뒤 비어있음), 나이스는 `clientKey:secretKey` (양쪽 모두 필수).

### 인증 실패 응답

| 상황 | HTTP | 응답 |
|------|------|------|
| Authorization 헤더 없음 | 401 | `{"resultCode":"U104","resultMsg":"사용자 인증에 실패하였습니다."}` |
| `Basic ` 접두사 없음 | 401 | 동일 |
| Base64 디코딩 실패 | 401 | `{"resultCode":"U104","resultMsg":"Base64 디코딩에 실패했습니다."}` |
| 콜론(`:`) 없거나 양쪽 값 없음 | 401 | `{"resultCode":"U104","resultMsg":"clientKey:secretKey 형식이어야 합니다."}` |

---

## 3. 결제 승인 API

실제 나이스페이먼츠에서는 JS SDK 결제창 → 인증 → returnUrl로 tid POST → 서버에서 승인 API 호출합니다.
Mock에서는 결제창 과정을 생략하고, 바로 승인 API를 호출하면 결제가 생성+승인됩니다.

### 요청

```
POST /v1/payments/{tid}
```

```bash
curl -X POST http://localhost:8091/v1/payments/nicuntct_test001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50000
  }'
```

| 파라미터 | 필수 | 타입 | 설명 |
|---------|------|------|------|
| `{tid}` (path) | O | string | 결제 인증 키 (URL path로 전달) |
| `amount` | O | number | 결제 금액 (1 이상) |

> **토스와의 차이**: 토스는 `POST /v1/payments/confirm` + body에 `paymentKey, orderId, amount`. 나이스는 `POST /v1/payments/{tid}` + body에 `amount`만.

### 성공 응답 (200)

```json
{
  "resultCode": "0000",
  "resultMsg": "정상 처리되었습니다.",
  "tid": "nicuntct_test001",
  "orderId": "ORDER-nicuntct_test001",
  "status": "paid",
  "amount": 50000,
  "balanceAmt": 50000,
  "goodsName": "상품-nicuntct_test001",
  "payMethod": "card",
  "currency": "KRW",
  "channel": "pc",
  "useEscrow": false,
  "paidAt": "2026-03-19T14:30:00+09:00",
  "failedAt": "0",
  "cancelledAt": "0",
  "approveNo": "12345678",
  "card": {
    "cardCode": "02",
    "cardName": "KB국민",
    "cardNum": "536112******1234",
    "cardQuota": "0",
    "isInterestFree": false,
    "cardType": "credit",
    "canPartCancel": "true",
    "acquCardCode": "02",
    "acquCardName": "KB국민"
  },
  "cancels": null,
  "receiptUrl": "https://mock-receipt.nicepay.co.kr/nicuntct_test001"
}
```

### 에러 응답

| HTTP | resultCode | 발생 조건 |
|------|-----------|-----------|
| 400 | `9000` | 필수 파라미터 누락, amount ≤ 0 |
| 400 | `A123` | 동일 tid로 다른 금액 재요청 |

---

## 4. 결제 조회 API

### tid로 조회

```
GET /v1/payments/{tid}
```

```bash
curl http://localhost:8091/v1/payments/nicuntct_test001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)"
```

### orderId로 조회

```
GET /v1/payments/find/{orderId}
```

```bash
curl http://localhost:8091/v1/payments/find/ORDER-nicuntct_test001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)"
```

### 에러 응답

| HTTP | resultCode | 발생 조건 |
|------|-----------|-----------|
| 404 | `A118` | 해당 거래 없음 |

> **카오스 모드 참고**: GET 요청은 기본적으로 카오스 모드의 영향을 받지 않습니다.

---

## 5. 결제 취소 API

### 요청

```
POST /v1/payments/{tid}/cancel
```

#### 전액 취소

```bash
curl -X POST http://localhost:8091/v1/payments/nicuntct_test001/cancel \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "고객 변심",
    "orderId": "ORDER-nicuntct_test001"
  }'
```

#### 부분 취소

```bash
curl -X POST http://localhost:8091/v1/payments/nicuntct_test001/cancel \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "부분 환불",
    "orderId": "ORDER-nicuntct_test001",
    "cancelAmt": 20000
  }'
```

| 파라미터 | 필수 | 타입 | 설명 |
|---------|------|------|------|
| `reason` | O | string | 취소 사유 |
| `orderId` | O | string | 상점 거래 고유번호 |
| `cancelAmt` | - | number | 부분취소 금액. 없으면 전액 취소 |

> **토스와의 차이**: 토스는 `cancelReason` + `cancelAmount`, 나이스는 `reason` + `orderId`(필수) + `cancelAmt`.

### 성공 응답 (200)

```json
{
  "resultCode": "0000",
  "resultMsg": "정상 처리되었습니다.",
  "tid": "nicuntct_test001",
  "cancelledTid": "nicuntct_test001-cancel-1",
  "status": "cancelled",
  "amount": 50000,
  "balanceAmt": 0,
  "cancels": [
    {
      "tid": "nicuntct_test001-cancel-1",
      "amount": 50000,
      "cancelledAt": "2026-03-19T14:35:00+09:00",
      "reason": "고객 변심"
    }
  ]
}
```

### 상태 변화

| 취소 유형 | status | balanceAmt |
|-----------|--------|------------|
| 부분 취소 | `partialCancelled` | 잔액 > 0 |
| 전액 취소 | `cancelled` | `0` |
| 부분 취소 여러 번 → 잔액 0 | `cancelled` | `0` |

### 에러 응답

| HTTP | resultCode | 발생 조건 |
|------|-----------|-----------|
| 400 | `9000` | reason 또는 orderId 누락 |
| 404 | `2012` | tid에 해당하는 거래 없음 |
| 400 | `2013` | 이미 전액 취소된 거래 |
| 403 | `2032` | cancelAmt > 남은 잔액 |

---

## 6. 에러 트리거

카오스 모드와 별개로, **특정 에러를 확정적으로 발생시키는 방법**입니다.

- 승인: `orderId`(= `ORDER-{tid}`)에 키워드 포함
- 취소: `reason`에 키워드 포함
- 대소문자 무시, 부분 문자열 매칭

### 승인 에러 트리거

```bash
# 카드 오류 — tid에 키워드를 넣으면 orderId가 ORDER-{tid}로 생성됨
curl -X POST http://localhost:8091/v1/payments/ORDER-card_error-001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"amount":5000}'
# → 400 {"resultCode":"3011","resultMsg":"카드번호 오류"}
```

#### 재시도 불가

| 키워드 | HTTP | resultCode | 설명 |
|--------|------|-----------|------|
| `card_error` | 400 | `3011` | 카드번호 오류 |
| `card_reject` | 400 | `3095` | 카드사 실패 응답 |
| `amount_error` | 400 | `3041` | 금액 오류 |
| `duplicate_order` | 400 | `A127` | 주문번호 중복 |
| `expired_session` | 400 | `A245` | 인증 시간 초과 |
| `amount_mismatch` | 400 | `A123` | 거래금액 불일치 |
| `auth_fail` | 401 | `U104` | 인증 실패 |

#### 재시도 가능

| 키워드 | HTTP | resultCode | 설명 |
|--------|------|-----------|------|
| `provider_error` | 500 | `A110` | 외부 연동 실패 |
| `system_error` | 500 | `9002` | 시스템 오류 |
| `socket_error` | 500 | `U508` | 소켓 연결 오류 |

### 취소 에러 트리거

```bash
curl -X POST http://localhost:8091/v1/payments/nicuntct_001/cancel \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"reason":"cancel_fail 테스트","orderId":"ORDER-nicuntct_001"}'
# → 400 {"resultCode":"2003","resultMsg":"취소 실패"}
```

| 키워드 | HTTP | resultCode | 설명 |
|--------|------|-----------|------|
| `cancel_fail` | 400 | `2003` | 취소 실패 |
| `cancel_system_error` | 500 | `9002` | 시스템 오류 |

---

## 7. 카오스 모드

카오스 모드는 **PG 서버 장애 상황을 시뮬레이션**합니다.
common 모듈의 카오스 엔진을 공유하므로 mock-toss와 동일한 기능을 제공합니다.

```bash
# 모드 조회
curl http://localhost:8091/chaos/mode

# 모드 변경
curl -X PUT "http://localhost:8091/chaos/mode?mode=DEAD"
curl -X PUT "http://localhost:8091/chaos/mode?mode=SLOW&slowMinMs=5000&slowMaxMs=15000"
curl -X PUT "http://localhost:8091/chaos/mode?mode=TIMEOUT"
curl -X PUT "http://localhost:8091/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=50"
curl -X PUT "http://localhost:8091/chaos/mode?mode=NORMAL"
```

| 모드 | 동작 |
|------|------|
| `NORMAL` | 정상 |
| `SLOW` | 랜덤 지연 (기본 3~10초) |
| `TIMEOUT` | 5분 무응답 |
| `DEAD` | 즉시 500 |
| `PARTIAL_FAILURE` | 확률적 실패 (기본 50%) |

> 자세한 카오스 모드 가이드는 [mock-toss api-guide.md](../../toss/mock/api-guide.md#8-카오스-모드)를 참고하세요. 동일한 common 모듈을 사용합니다.

---

## 8. 테스트 초기화 API

테스트 간 격리를 위해 Mock 서버의 상태를 초기화합니다.

### 요청

```
DELETE /test/reset → 204 No Content
```

```bash
curl -X DELETE http://localhost:8091/test/reset
```

> 인증 불필요. 테스트 전용 엔드포인트입니다.

### 초기화 대상

| 대상 | 설명 |
|------|------|
| 인메모리 결제 저장소 | 전체 삭제 |
| 카오스 설정 | 기본값 복구 (NORMAL, 3000~10000ms, 50%, affectReadApis=false) |

### 사용 시나리오

```java
@BeforeEach
void setUp() {
    // 테스트 간 상태 격리
    restClient.delete().uri("/test/reset").retrieve().toBodilessEntity();
}
```

---

## 9. 테스트 시나리오 레시피

### 시나리오 1: 타임아웃 → 조회 → 재시도

```bash
# 1) 타임아웃 모드
curl -X PUT "http://localhost:8091/chaos/mode?mode=TIMEOUT"

# 2) 승인 호출 → 타임아웃 발생
curl -X POST http://localhost:8091/v1/payments/nicuntct_timeout \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"amount":30000}'
# → 타임아웃 (응답 못 받음)

# 3) 조회로 상태 확인 (GET은 카오스 영향 없음)
curl http://localhost:8091/v1/payments/nicuntct_timeout \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)"
# → 404 (결제 생성 안 됨)

# 4) 복구 후 재시도
curl -X PUT "http://localhost:8091/chaos/mode?mode=NORMAL"

curl -X POST http://localhost:8091/v1/payments/nicuntct_timeout \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"amount":30000}'
# → 200 정상 승인
```

### 시나리오 2: 서킷브레이커 OPEN → 복구

```bash
# 1) DEAD 모드로 장애 발생
curl -X PUT "http://localhost:8091/chaos/mode?mode=DEAD"

# 2) 승인 연속 호출 → 모두 500
# → slidingWindow 실패율 100% → 서킷 OPEN

# 3) 복구
curl -X PUT "http://localhost:8091/chaos/mode?mode=NORMAL"

# 4) waitDurationInOpenState 경과 → HALF_OPEN → 성공 → CLOSED
```

### 시나리오 3: 재시도 정책 검증

```bash
# 재시도 가능 에러
curl -X POST http://localhost:8091/v1/payments/ORDER-provider_error-001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"amount":5000}'
# → 500 resultCode=A110 (재시도 해야 함)

# 재시도 불가 에러
curl -X POST http://localhost:8091/v1/payments/ORDER-card_error-001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"amount":5000}'
# → 400 resultCode=3011 (재시도하면 안 됨)
```

---

## 토스 vs 나이스 비교 (클라이언트 코드 전환 시 참고)

| | mock-toss (8090) | mock-nice (8091) |
|---|---|---|
| 승인 | `POST /v1/payments/confirm` | `POST /v1/payments/{tid}` |
| 승인 body | `{paymentKey, orderId, amount}` | `{amount}` |
| 성공 판단 | HTTP 200 + status `DONE` | HTTP 200 + resultCode `0000` |
| 에러 포맷 | `{code, message}` | `{resultCode, resultMsg}` |
| 인증 | `Basic(secretKey:)` | `Basic(clientKey:secretKey)` |
| 취소 필수값 | `cancelReason` | `reason` + `orderId` |
| 취소 금액 | `cancelAmount` | `cancelAmt` |
| 상태값 | `DONE`/`CANCELED`/`PARTIAL_CANCELED` | `paid`/`cancelled`/`partialCancelled` |

---

## 카오스 모드 vs 에러 트리거 비교

| | 카오스 모드 | 에러 트리거 |
|---|---|---|
| **대상** | 모든 요청 (글로벌) | 특정 요청 (키워드 매칭) |
| **확정성** | DEAD: 확정 실패, PARTIAL_FAILURE: 확률적 | 항상 확정적 |
| **에러 종류** | 고정 (500, PARTIAL_FAILURE 풀) | 나이스 공식 에러 코드 전체 |
| **설정 방법** | `PUT /chaos/mode` 또는 `X-CHAOS-MODE` 헤더 | orderId(=tid) / reason에 키워드 |
| **용도** | Resilience4j 서킷/타임아웃/재시도 테스트 | 비즈니스 에러 핸들링 테스트 |

> **적용 순서**: 카오스 인터셉터(글로벌) → 에러 트리거(서비스 레이어). 카오스 모드가 DEAD면 에러 트리거까지 도달하지 않습니다.
