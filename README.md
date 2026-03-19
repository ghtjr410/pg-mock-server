# pg-mock-server

**토스페이먼츠 / 나이스페이먼츠 결제 API를 그대로 재현하는 Mock 서버.**

실무 결제 코드에서 `base-url`만 바꾸면 장애 시뮬레이션(서킷브레이커, 타임아웃, 재시도) 테스트가 가능합니다.
코드 변경 없이 프로필만 전환하세요.

---

## 목차

- [시작하기](#시작하기)
- [실무 프로젝트 연결](#실무-프로젝트-연결)
- [API 사용법](#api-사용법)
  - [mock-toss (토스페이먼츠)](#mock-toss-토스페이먼츠)
  - [mock-nice (나이스페이먼츠)](#mock-nice-나이스페이먼츠)
- [장애 시뮬레이션 (카오스 모드)](#장애-시뮬레이션-카오스-모드)
- [에러 트리거](#에러-트리거)
- [테스트 시나리오 예시](#테스트-시나리오-예시)
- [프로젝트 구조](#프로젝트-구조)

---

## 시작하기

### 필요 환경

- **Java 21** 이상
- **Docker** (Docker 실행 방식 사용 시)

### 방법 1: Gradle로 직접 실행

```bash
git clone https://github.com/ghtjr410/pg-mock-server.git
cd pg-mock-server

# 토스 Mock 서버 실행 (포트 8090)
./gradlew :mock-toss:bootRun

# 나이스 Mock 서버 실행 (새 터미널에서, 포트 8091)
./gradlew :mock-nice:bootRun
```

### 방법 2: Docker Compose로 한 번에 실행

```bash
./gradlew :mock-toss:build :mock-nice:build -x test
docker compose up
```

### 방법 3: Docker Hub에서 바로 실행 (mock-toss)

```bash
docker run -p 8090:8090 ghtjr410/mock-toss:latest
```

### 실행 확인

```bash
# 200 OK가 나오면 성공
curl http://localhost:8090/chaos/mode
curl http://localhost:8091/chaos/mode
```

---

## 실무 프로젝트 연결

실무 프로젝트의 `application-local.yml`에서 base-url만 Mock 서버로 바꾸면 됩니다.

```yaml
payment:
  toss:
    base-url: http://localhost:8090   # ← mock-toss
    secret-key: test_sk_xxxx          # 아무 값이나 OK (형식만 맞으면 됨)
  nice:
    base-url: http://localhost:8091   # ← mock-nice
    client-key: testClientKey
    secret-key: testSecretKey
```

**실무 코드 변경: 없음.** 스프링 프로필만 `local`로 전환하세요.

---

## API 사용법

### mock-toss (토스페이먼츠)

> 포트: `8090`
> 인증: `Authorization: Basic {Base64(시크릿키:)}`  ← 시크릿키 뒤에 콜론 필수

인증 헤더 만드는 법:
```bash
# test_sk_xxxx: 를 Base64 인코딩
echo -n 'test_sk_xxxx:' | base64
# → dGVzdF9za194eHh4Og==
```

#### 결제 승인

```bash
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentKey": "tpk_001",
    "orderId": "ORDER-001",
    "amount": 50000
  }'
```

응답 예시:
```json
{
  "paymentKey": "tpk_001",
  "orderId": "ORDER-001",
  "status": "DONE",
  "totalAmount": 50000,
  "method": "카드",
  "approvedAt": "2026-03-20T14:30:00+09:00"
}
```

#### 결제 조회

```bash
# paymentKey로 조회
curl http://localhost:8090/v1/payments/tpk_001 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"

# orderId로 조회
curl http://localhost:8090/v1/payments/orders/ORDER-001 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"
```

#### 결제 취소

```bash
# 전액 취소
curl -X POST http://localhost:8090/v1/payments/tpk_001/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason": "고객 요청"}'

# 부분 취소 (3,000원만)
curl -X POST http://localhost:8090/v1/payments/tpk_001/cancel \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"cancelReason": "부분 환불", "cancelAmount": 3000}'
```

---

### mock-nice (나이스페이먼츠)

> 포트: `8091`
> 인증: `Authorization: Basic {Base64(clientKey:secretKey)}`  ← 양쪽 다 필요

인증 헤더 만드는 법:
```bash
echo -n 'testClientKey:testSecretKey' | base64
# → dGVzdENsaWVudEtleTp0ZXN0U2VjcmV0S2V5
```

#### 결제 승인

```bash
curl -X POST http://localhost:8091/v1/payments/nicuntct_001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"amount": 50000}'
```

#### 거래 조회

```bash
# tid로 조회
curl http://localhost:8091/v1/payments/nicuntct_001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)"

# orderId로 조회
curl http://localhost:8091/v1/payments/find/ORDER-nicuntct_001 \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)"
```

#### 결제 취소

```bash
# 전액 취소
curl -X POST http://localhost:8091/v1/payments/nicuntct_001/cancel \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"reason": "고객 요청", "orderId": "ORDER-nicuntct_001"}'

# 부분 취소
curl -X POST http://localhost:8091/v1/payments/nicuntct_001/cancel \
  -H "Authorization: Basic $(echo -n 'testClientKey:testSecretKey' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"reason": "부분 환불", "orderId": "ORDER-nicuntct_001", "cancelAmt": 3000}'
```

---

## 장애 시뮬레이션 (카오스 모드)

Mock 서버의 핵심 기능입니다. **카오스 모드를 바꾸면 모든 POST/PUT 요청에 장애가 적용됩니다.**
GET(조회) 요청은 기본적으로 장애 영향을 받지 않습니다.

### 5가지 카오스 모드

| 모드 | 무슨 일이 일어나는가 | 테스트 대상 |
|------|---------------------|------------|
| `NORMAL` | 정상 응답 (기본값) | — |
| `SLOW` | 3~10초 랜덤 지연 후 정상 응답 | slowCall 감지, TimeLimiter |
| `TIMEOUT` | 응답을 아예 안 줌 (5분 대기) | readTimeout, 타임아웃 복구 |
| `DEAD` | 즉시 500 에러 | CircuitBreaker OPEN 전이 |
| `PARTIAL_FAILURE` | N% 확률로 실패 (기본 50%) | slidingWindow 실패율 계산 |

### 카오스 모드 사용법

```bash
# 현재 모드 확인
curl http://localhost:8090/chaos/mode

# DEAD 모드로 변경 — 모든 결제 요청이 500 에러
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD"

# SLOW 모드 — 5~15초 지연
curl -X PUT "http://localhost:8090/chaos/mode?mode=SLOW&slowMinMs=5000&slowMaxMs=15000"

# PARTIAL_FAILURE — 70% 확률로 실패
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=70"

# 조회 API에도 장애 적용 (기본은 GET 제외)
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD&affectReadApis=true"

# 정상으로 복구
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"
```

### 요청 하나만 장애 주기 (`X-CHAOS-MODE` 헤더)

글로벌 설정은 그대로 두고, **특정 요청에만** 장애를 적용할 수 있습니다:

```bash
# 글로벌은 NORMAL인데, 이 요청만 500 에러
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "X-CHAOS-MODE: DEAD" \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_x","orderId":"ORDER-X","amount":10000}'

# 글로벌은 DEAD인데, 이 요청만 정상 처리
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "X-CHAOS-MODE: NORMAL" \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_y","orderId":"ORDER-Y","amount":10000}'
```

### 테스트 초기화

테스트 간 격리를 위해 서버 상태를 초기화합니다. (인증 불필요)

```bash
curl -X DELETE http://localhost:8090/test/reset   # mock-toss
curl -X DELETE http://localhost:8091/test/reset   # mock-nice
```

초기화 대상: 결제 저장소, 멱등키 캐시(toss), 카오스 설정 → 전부 기본값으로 복원.

---

## 에러 트리거

카오스 모드와 별개로, **요청 필드에 특정 키워드를 넣으면** 해당 PG 에러를 정확히 재현합니다.
실제 PG가 반환하는 에러 코드와 메시지를 그대로 사용합니다.

### 사용법

```bash
# orderId에 "reject_company" 키워드 포함 → 카드사 거절 에러
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_err","orderId":"ORDER-reject_company-001","amount":5000}'
```
```json
{"code": "REJECT_CARD_COMPANY", "message": "결제 승인이 거절되었습니다"}
```

### mock-toss 에러 키워드

**승인 API** — `orderId`에 키워드 포함:

| 키워드 | HTTP | 에러코드 | 재시도 가능 |
|--------|------|---------|:----------:|
| `already_processed` | 400 | `ALREADY_PROCESSED_PAYMENT` | |
| `invalid_card` | 400 | `INVALID_CARD_NUMBER` | |
| `stopped_card` | 400 | `INVALID_STOPPED_CARD` | |
| `expired_card` | 400 | `INVALID_CARD_EXPIRATION` | |
| `reject_card` | 400 | `INVALID_REJECT_CARD` | |
| `exceed` | 400 | `EXCEED_MAX_AMOUNT` | |
| `lost_stolen` | 400 | `INVALID_CARD_LOST_OR_STOLEN` | |
| `unapproved` | 400 | `UNAPPROVED_ORDER_ID` | |
| `reject_payment` | 403 | `REJECT_CARD_PAYMENT` | |
| `reject_company` | 403 | `REJECT_CARD_COMPANY` | |
| `forbidden` | 403 | `FORBIDDEN_REQUEST` | |
| `not_found_session` | 404 | `NOT_FOUND_PAYMENT_SESSION` | |
| `provider_error` | 400 | `PROVIDER_ERROR` | O |
| `card_processing` | 400 | `CARD_PROCESSING_ERROR` | O |
| `system_error` | 500 | `FAILED_INTERNAL_SYSTEM_PROCESSING` | O |
| `payment_processing` | 500 | `FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING` | O |
| `unknown_error` | 500 | `UNKNOWN_PAYMENT_ERROR` | O |

**취소 API** — `cancelReason`에 키워드 포함:

| 키워드 | HTTP | 에러코드 |
|--------|------|---------|
| `not_cancelable_amount` | 403 | `NOT_CANCELABLE_AMOUNT` |
| `not_cancelable` | 403 | `NOT_CANCELABLE_PAYMENT` |
| `cancel_system_error` | 500 | `FAILED_INTERNAL_SYSTEM_PROCESSING` |
| `cancel_method_error` | 500 | `FAILED_METHOD_HANDLING_CANCEL` |

### mock-nice 에러 키워드

**승인 API** — `tid`에 키워드 포함:

| 키워드 | HTTP | resultCode | 재시도 가능 |
|--------|------|-----------|:----------:|
| `card_error` | 400 | `3011` | |
| `card_reject` | 400 | `3095` | |
| `amount_error` | 400 | `3041` | |
| `duplicate_order` | 400 | `A127` | |
| `expired_session` | 400 | `A245` | |
| `amount_mismatch` | 400 | `A123` | |
| `auth_fail` | 401 | `U104` | |
| `provider_error` | 500 | `A110` | O |
| `system_error` | 500 | `9002` | O |
| `socket_error` | 500 | `U508` | O |

**취소 API** — `reason`에 키워드 포함:

| 키워드 | HTTP | resultCode |
|--------|------|-----------|
| `cancel_fail` | 400 | `2003` |
| `cancel_system_error` | 500 | `9002` |

---

## 테스트 시나리오 예시

### 시나리오 1: PG 완전 장애 → 서킷브레이커 OPEN → 복구

```bash
# 1) 장애 발생시키기
curl -X PUT "http://localhost:8090/chaos/mode?mode=DEAD"

# 2) 결제 승인 5건 호출 → 모두 500
#    → CircuitBreaker가 실패율 100%를 감지 → OPEN 상태 전이
#    → 이후 요청은 서버에 가지 않고 즉시 차단 (fallback 실행)

# 3) 복구
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"

# 4) waitDurationInOpenState 경과 후 HALF_OPEN → 성공 확인 → CLOSED
```

### 시나리오 2: 결제 승인 타임아웃 → 조회로 상태 확인

```bash
# 1) 타임아웃 모드 (서버가 응답을 안 줌)
curl -X PUT "http://localhost:8090/chaos/mode?mode=TIMEOUT"

# 2) confirm 호출 → 클라이언트 readTimeout 초과 → ResourceAccessException
#    ❓ 결제가 됐는지 안 됐는지 모르는 상황

# 3) 조회 API로 확인 (GET은 카오스 적용 안 됨!)
curl http://localhost:8090/v1/payments/tpk_001 \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)"

# 4) 복구
curl -X PUT "http://localhost:8090/chaos/mode?mode=NORMAL"
```

### 시나리오 3: 간헐적 장애에서 서킷 전이 관찰

```bash
# 50% 확률로 실패
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=50"
# → slidingWindow 내 실패율이 threshold를 넘으면 서킷 OPEN

# 실패율을 낮추면 서킷은 CLOSED 유지
curl -X PUT "http://localhost:8090/chaos/mode?mode=PARTIAL_FAILURE&partialFailureRate=10"
```

### 시나리오 4: 비즈니스 에러는 재시도하지 않기

```bash
# 카드사 거절은 재시도해도 소용없음 → retryExceptions에서 제외해야 함
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_biz","orderId":"ORDER-reject_company-001","amount":5000}'
# → 403 (재시도 대상 아님)

# 서버 내부 에러는 재시도 가능
curl -X POST http://localhost:8090/v1/payments/confirm \
  -H "Authorization: Basic $(echo -n 'test_sk_xxxx:' | base64)" \
  -H "Content-Type: application/json" \
  -d '{"paymentKey":"tpk_sys","orderId":"ORDER-system_error-001","amount":5000}'
# → 500 (재시도 대상)
```

---

## 프로젝트 구조

```
pg-mock-server/
├── common/              ← 카오스 시뮬레이션 엔진 (전 모듈 공유)
│   └── ChaosInterceptor, ChaosMode, ChaosController
├── mock-toss/           ← :8090 | 토스페이먼츠 결제 API Mock
├── mock-nice/           ← :8091 | 나이스페이먼츠 결제 API Mock
├── docs/
│   ├── toss/spec/       ← 토스 공식 API 스펙
│   ├── toss/mock/       ← Mock 구현 가이드
│   ├── nice/spec/       ← 나이스 공식 API 스펙
│   └── nice/mock/       ← Mock 구현 가이드
├── docker-compose.yml
└── README.md
```

## 기술 스택

- Java 21, Spring Boot 3.4.3
- Gradle Kotlin DSL 멀티모듈
- ConcurrentHashMap 인메모리 저장소 (DB 없음)
- 외부 의존성 없음 (Spring Boot Starter Web/Test만 사용)
