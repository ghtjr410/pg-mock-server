# pg-mock-server — Claude Code 맥락 문서

## 프로젝트 목적

실무에서 운영 중인 **토스페이먼츠(동기 일반결제)**와 **KG이니시스(비동기 정기결제)**를 시뮬레이션하는 Mock PG 서버.
Resilience4j(타임아웃, 서킷브레이커, 재시도, 폴백) 테스트용이며, 실무 코드 한 줄 안 바꾸고 URL만 전환해서 사용한다.

---

## 기술 스택

- Java 21, Spring Boot 3.x
- Gradle Kotlin DSL, 멀티모듈
- H2 (테스트용 인메모리) 또는 JPA 없이 인메모리 Map 저장도 가능
- 외부 의존성 최소화 (이건 PG "흉내"를 내는 서버일 뿐)

---

## 멀티모듈 구조

```
pg-mock-server/
├── settings.gradle.kts
├── build.gradle.kts          ← 루트 (공통 의존성)
├── common/                   ← 카오스 모드, 지연 시뮬레이션, 공통 유틸
│   └── build.gradle.kts
├── mock-toss/                ← :8090 | 토스페이먼츠 API Mock
│   └── build.gradle.kts
├── mock-kg-inicis/           ← :8091 | KG이니시스 빌링 API Mock
│   └── build.gradle.kts
└── CLAUDE-CONTEXT.md         ← 이 파일
```

---

## 카오스 모드 (common 모듈)

모든 Mock 서버가 공유하는 장애 시뮬레이션 엔진.
**HTTP 헤더 또는 설정 파일**로 모드 전환 가능해야 한다.

| 모드 | 동작 | 용도 |
|------|------|------|
| `NORMAL` | 정상 응답, 지연 없음 | 기본 동작 확인 |
| `SLOW` | 응답 지연 3~10초 (랜덤) | 타임아웃 테스트 |
| `TIMEOUT` | 응답 안 줌 (무한 대기 or 읽기 타임아웃 초과) | 타임아웃 + 서킷브레이커 |
| `DEAD` | 즉시 5xx 에러 | 서킷브레이커 OPEN 전환 |
| `PARTIAL_FAILURE` | 요청의 N%만 실패 (설정 가능) | 서킷브레이커 slidingWindow 테스트 |

### 카오스 설정 방식
```yaml
# application.yml 예시
chaos:
  mode: NORMAL           # NORMAL | SLOW | TIMEOUT | DEAD | PARTIAL_FAILURE
  slow-min-ms: 3000
  slow-max-ms: 10000
  partial-failure-rate: 50  # PARTIAL_FAILURE 모드에서 실패 비율 (%)
```

### 런타임 전환 API (선택)
```
PUT /chaos/mode?mode=SLOW
GET /chaos/mode
```

---

## mock-toss 모듈 — 토스페이먼츠 Mock

### 실무 맥락
- 동기 일반결제: 사용자가 결제창에서 인증 → 프론트가 successUrl로 리다이렉트 → **백엔드가 confirm(승인) API 호출**
- confirm은 **동기 요청-응답**. 성공하면 즉시 JSON 응답.
- Read Timeout 60초 권장 (PG 내부에서 카드사/은행 통신 포함)

### Mock 해야 할 토스 API

#### 1. 결제 승인 (핵심)
```
POST /v1/payments/confirm
Authorization: Basic {시크릿키:를 Base64}
Content-Type: application/json

{
  "paymentKey": "tpk_xxxx",
  "orderId": "ORDER-001",
  "amount": 50000
}
```

**정상 응답 (200)**:
```json
{
  "paymentKey": "tpk_xxxx",
  "orderId": "ORDER-001",
  "status": "DONE",
  "totalAmount": 50000,
  "method": "카드",
  "approvedAt": "2025-01-01T12:00:00+09:00",
  "card": {
    "issuerCode": "11",
    "number": "1234-****-****-5678",
    "approveNo": "00000000"
  }
}
```

**실패 응답 (4xx/5xx)**:
```json
{
  "code": "REJECT_CARD_COMPANY",
  "message": "카드사에서 거절했습니다."
}
```

주요 에러 코드:
- `REJECT_CARD_COMPANY` — 카드사 거절
- `EXCEED_MAX_AMOUNT` — 한도 초과
- `INVALID_CARD_NUMBER` — 카드번호 오류
- `PROVIDER_ERROR` — PG 내부 오류 (5xx)
- `FAILED_INTERNAL_SYSTEM_PROCESSING` — 시스템 오류

#### 2. 결제 조회
```
GET /v1/payments/{paymentKey}
Authorization: Basic {시크릿키:를 Base64}
```

응답: confirm 응답과 동일 구조, status 필드로 상태 확인

#### 3. 결제 취소
```
POST /v1/payments/{paymentKey}/cancel
Authorization: Basic {시크릿키:를 Base64}
Content-Type: application/json

{
  "cancelReason": "고객 요청"
}
```

### Mock 동작 규칙
- `paymentKey`, `orderId`, `amount` 세 값이 일치해야 승인 성공 (금액 불일치 → 에러)
- 인메모리 저장소에 결제 건 관리 (Map<paymentKey, Payment>)
- **카오스 모드에 따라 지연/실패/타임아웃 시뮬레이션**
- 멱등키(Idempotency-Key) 헤더 지원하면 Nice-to-Have

---

## mock-kg-inicis 모듈 — KG이니시스 빌링 Mock

### 실무 맥락
- 비동기 정기결제(빌링): 배치로 빌링키 이용해서 결제 요청
- 사용자 없음 (서버 to 서버)
- 실무에서 OkHttpClient 60초 타임아웃으로 연동 중

### Mock 해야 할 이니시스 API

#### 1. 빌링 결제 요청
```
POST /api/v1/billing/pay
Content-Type: application/json

{
  "billingKey": "blk_xxxx",
  "orderId": "ORDER-001",
  "amount": 30000,
  "productName": "월간 구독",
  "buyerName": "홍길동"
}
```

**정상 응답 (200)**:
```json
{
  "resultCode": "00",
  "resultMsg": "정상",
  "tid": "INI20250101120000001",
  "orderId": "ORDER-001",
  "amount": 30000,
  "approvedAt": "20250101120000",
  "cardNo": "1234-****-****-5678"
}
```

**실패 응답**:
```json
{
  "resultCode": "V110",
  "resultMsg": "한도초과",
  "tid": null,
  "orderId": "ORDER-001"
}
```

주요 resultCode:
- `00` — 성공
- `V110` — 한도초과
- `V120` — 카드 유효기간 오류
- `V130` — 카드번호 오류
- `E001` — 시스템 오류

#### 2. 거래 조회
```
POST /api/v1/billing/inquiry
Content-Type: application/json

{
  "tid": "INI20250101120000001"
}
```

### Mock 동작 규칙
- billingKey 기반 인메모리 저장
- 카오스 모드 공유
- 실패 시 다음 날 재시도하는 시나리오 테스트 가능하도록 설계

---

## 과제용 PG 시뮬레이터와의 차이

| 항목 | 과제 PG 시뮬레이터 | 이 Mock 서버 |
|------|-------------------|-------------|
| 방식 | 비동기 콜백 | 토스: 동기, 이니시스: 동기(빌링) |
| 언어 | Kotlin | Java 21 |
| 성공률 | 고정 70% (랜덤) | 카오스 모드로 조절 가능 |
| 장애 시뮬레이션 | 없음 | SLOW/TIMEOUT/DEAD/PARTIAL |
| API 스펙 | 자체 스펙 | 토스/이니시스 실제 스펙 모사 |
| 목적 | 과제 제출 | 포트폴리오 + 실무 Resilience 테스트 |

---

## 실무 프로젝트에서의 사용 방법

```yaml
# 실무 application-test.yml
payment:
  toss:
    base-url: http://localhost:8090  # ← mock-toss
    secret-key: test_sk_xxxx
  kg-inicis:
    base-url: http://localhost:8091  # ← mock-kg-inicis
```

실무 코드는 한 줄도 안 바꾼다. profile이나 설정만 전환.

---

## 우선순위

1. **mock-toss confirm API** — 실무에서 즉시 Resilience4j 테스트 가능
2. **common 카오스 모듈** — 모드 전환으로 다양한 장애 시나리오
3. **mock-kg-inicis billing API** — 정기결제 Resilience 테스트
4. **런타임 카오스 전환 API** — 테스트 편의성

---

## Resilience4j 테스트 시나리오 매핑

| 시나리오 | 카오스 모드 | 검증 대상 |
|----------|-----------|----------|
| PG 느린 응답 → 타임아웃 작동 | SLOW | connectTimeout, readTimeout |
| PG 완전 장애 → 서킷 OPEN | DEAD | CircuitBreaker failureRateThreshold |
| PG 간헐적 장애 → 서킷 전이 | PARTIAL_FAILURE (50%) | slidingWindowSize, HALF-OPEN |
| 서킷 OPEN → 폴백 응답 | DEAD → 확인 후 NORMAL | fallback 메시지 |
| 조회 API 재시도 | PARTIAL_FAILURE (30%) | Retry with backoff |
| 타임아웃 후 PENDING 유지 | TIMEOUT | 상태 조회로 최종 판단 |

---

## 참고: 토스 타임아웃 실무 판단

- Read Timeout 60초 (토스 공식 권장)
- 타임아웃 ≠ 실패. PENDING 유지 → 조회 API로 확인
- 멱등키 없이 재시도 → 이중결제 위험 → **confirm은 재시도 금지**
- 조회(GET)는 자연 멱등 → 재시도 가능, 3회

---

## 작업 지시

위 구조대로 멀티모듈 프로젝트를 세팅하고, mock-toss의 confirm API부터 구현해주세요.
카오스 모드는 common 모듈에 만들고 mock-toss에서 import해서 사용합니다.
인메모리 저장소(ConcurrentHashMap)로 충분합니다. DB 불필요.