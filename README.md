# pg-mock-server

토스페이먼츠 / KG이니시스 Mock PG 서버.
실무 코드 한 줄 안 바꾸고 `base-url`만 전환하여 Resilience4j(타임아웃, 서킷브레이커, 재시도) 테스트.

---

## Quick Start

```bash
# mock-toss (:8090)
./gradlew :mock-toss:bootRun

# mock-kg-inicis (:8091)
./gradlew :mock-kg-inicis:bootRun

# 또는 Docker로 동시 실행
./gradlew :mock-toss:build :mock-kg-inicis:build -x test
docker compose up
```

---

## 실무 프로젝트 연결

실무 프로젝트의 `application-local.yml`에 base-url만 변경:

```yaml
payment:
  toss:
    base-url: http://localhost:8090   # mock-toss
    secret-key: test_sk_xxxx         # 아무 값이나 OK
  kg-inicis:
    base-url: http://localhost:8091   # mock-kg-inicis
```

실무 코드 변경: **없음**. 프로필만 전환.

---

## mock-toss API

모든 API에 `Authorization: Basic {시크릿키:를 Base64}` 헤더 필요.

### 결제 승인

```bash
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_001","orderId":"ORDER-001","amount":50000}'
```

### 결제 조회 (paymentKey)

```bash
curl http://localhost:8090/v1/payments/tpk_001 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"
```

### 결제 조회 (orderId)

```bash
curl http://localhost:8090/v1/payments/orders/ORDER-001 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"
```

### 결제 취소 (전액)

```bash
curl -X POST http://localhost:8090/v1/payments/tpk_001/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason":"고객 요청"}'
```

### 결제 취소 (부분)

```bash
curl -X POST http://localhost:8090/v1/payments/tpk_001/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason":"부분 환불","cancelAmount":3000}'
```

---

## mock-kg-inicis API

### 빌링 결제

```bash
curl -X POST http://localhost:8091/api/v1/billing/pay \
  -H "Content-Type: application/json" \
  -d '{
    "billingKey":"blk_001","orderId":"ORDER-001",
    "amount":30000,"productName":"월간 구독","buyerName":"홍길동"
  }'
```

### 거래 조회

```bash
curl -X POST http://localhost:8091/api/v1/billing/inquiry \
  -H "Content-Type: application/json" \
  -d '{"tid":"INI20250101120000001"}'
```

---

## 카오스 모드

모든 Mock 서버가 공유하는 장애 시뮬레이션. POST/PUT 요청에만 적용되고, GET(조회)은 기본적으로 영향 안 받음.

| 모드 | 동작 | 용도 |
|------|------|------|
| `NORMAL` | 정상 응답 | 기본 |
| `SLOW` | 3~10초 랜덤 지연 후 응답 | 타임아웃 테스트 |
| `TIMEOUT` | 응답 안 줌 (5분 대기) | 타임아웃 + 서킷브레이커 |
| `DEAD` | 즉시 500 에러 | 서킷브레이커 OPEN |
| `PARTIAL_FAILURE` | N% 확률로 실패 | slidingWindow 테스트 |

### 카오스 모드 조회

```bash
curl http://localhost:8090/chaos/mode
```

### 카오스 모드 변경

```bash
# DEAD 모드
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD"

# SLOW 모드 (5~15초 지연)
curl -X PUT "http://localhost:8090/chaos/mode?mode=SLOW&slowMinMs=5000&slowMaxMs=15000"

# 50% 확률 실패
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=50"

# 조회 API에도 카오스 적용
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD&affectReadApis=true"

# 정상으로 복구
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"
```

### 요청별 카오스 오버라이드

글로벌 설정과 별개로, 개별 요청에 `X-CHAOS-MODE` 헤더를 붙이면 해당 요청만 카오스 적용:

```bash
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "X-CHAOS-MODE: DEAD" \
  -H "Authorization: Basic ..." \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_002","orderId":"ORDER-002","amount":10000}'
```

---

## 에러 트리거

orderId(confirm) 또는 cancelReason(cancel)에 키워드를 포함시키면 해당 에러를 반환.
카오스 모드와 독립적으로 동작.

### mock-toss confirm 에러

| 키워드 | HTTP | 에러코드 | 재시도 |
|--------|------|---------|--------|
| `already_processed` | 400 | `ALREADY_PROCESSED_PAYMENT` | X |
| `invalid_card` | 400 | `INVALID_CARD_NUMBER` | X |
| `stopped_card` | 400 | `INVALID_STOPPED_CARD` | X |
| `expired_card` | 400 | `INVALID_CARD_EXPIRATION` | X |
| `reject_card` | 400 | `INVALID_REJECT_CARD` | X |
| `exceed` | 400 | `EXCEED_MAX_AMOUNT` | X |
| `lost_stolen` | 400 | `INVALID_CARD_LOST_OR_STOLEN` | X |
| `unapproved` | 400 | `UNAPPROVED_ORDER_ID` | X |
| `reject_payment` | 403 | `REJECT_CARD_PAYMENT` | X |
| `reject_company` | 403 | `REJECT_CARD_COMPANY` | X |
| `forbidden` | 403 | `FORBIDDEN_REQUEST` | X |
| `not_found_session` | 404 | `NOT_FOUND_PAYMENT_SESSION` | X |
| `provider_error` | 400 | `PROVIDER_ERROR` | O |
| `card_processing` | 400 | `CARD_PROCESSING_ERROR` | O |
| `system_error` | 500 | `FAILED_INTERNAL_SYSTEM_PROCESSING` | O |
| `payment_processing` | 500 | `FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING` | O |
| `unknown_error` | 500 | `UNKNOWN_PAYMENT_ERROR` | O |

```bash
# 예: 카드사 거절 에러 발생
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_err","orderId":"ORDER-reject_company-001","amount":5000}'
# → 403 {"code":"REJECT_CARD_COMPANY","message":"결제 승인이 거절되었습니다"}
```

### mock-toss cancel 에러

cancelReason에 키워드 포함:

| 키워드 | HTTP | 에러코드 |
|--------|------|---------|
| `not_cancelable_amount` | 403 | `NOT_CANCELABLE_AMOUNT` |
| `not_cancelable` | 403 | `NOT_CANCELABLE_PAYMENT` |
| `cancel_system_error` | 500 | `FAILED_INTERNAL_SYSTEM_PROCESSING` |
| `cancel_method_error` | 500 | `FAILED_METHOD_HANDLING_CANCEL` |

### mock-kg-inicis 에러

orderId에 키워드 포함:

| 키워드 | resultCode | resultMsg |
|--------|-----------|-----------|
| `limit` | `V110` | 한도초과 |
| `expired` | `V120` | 카드 유효기간 오류 |
| `badcard` | `V130` | 카드번호 오류 |
| `syserr` | `E001` | 시스템 오류 |

---

## 테스트 시나리오 예시

### 1. PG 완전 장애 → 서킷브레이커 OPEN → 복구

```bash
# 1) 장애 발생
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD"
# 2) 결제 승인 5건 호출 → 모두 500 → 서킷 OPEN → fallback 실행
# 3) 복구
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"
# 4) waitDurationInOpenState 경과 후 → HALF_OPEN → CLOSED
```

### 2. 타임아웃 → 조회로 상태 확인

```bash
# 1) 타임아웃 모드
curl -X PUT "http://localhost:8090/chaos/mode?mode=TIMEOUT"
# 2) confirm 호출 → 클라이언트 readTimeout 초과 → 타임아웃
# 3) 조회 API는 정상 동작 (GET은 카오스 적용 안됨)
curl http://localhost:8090/v1/payments/tpk_001 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"
# 4) 복구
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"
```

### 3. 간헐적 장애 → 서킷 전이

```bash
# 50% 실패율
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=50"
# slidingWindowSize 내 실패율 50% 도달 → 서킷 OPEN
# 실패율 낮추면 → CLOSED 유지
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=10"
```

---

## 프로젝트 구조

```
pg-mock-server/
├── common/              ← 카오스 모드 엔진 (ChaosInterceptor, ChaosController)
├── mock-toss/           ← :8090 | 토스페이먼츠 API Mock
├── mock-kg-inicis/      ← :8091 | KG이니시스 빌링 API Mock
├── docker-compose.yml
└── README.md
```

## 기술 스택

- Java 21, Spring Boot 3.4.3
- Gradle Kotlin DSL, 멀티모듈
- ConcurrentHashMap 인메모리 저장소 (DB 없음)
- 외부 의존성 없음
