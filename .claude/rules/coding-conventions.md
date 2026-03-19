## 코딩 컨벤션

### 기술 스택
- **Java 21**, Spring Boot 3.4.3, Gradle Kotlin DSL
- **외부 의존성 없음** — Spring Boot Starter Web/Test만 사용
- **인메모리 저장소** — ConcurrentHashMap 기반, DB 없음
- DTO는 Java `record` 사용

### 패키지 구조 패턴

각 mock 모듈은 아래 레이어드 구조를 따른다. (참고: loopers/pg-simulator 헥사고날 아키텍처)

```
com.pgmock.{pg}/
├── controller/      ← 라우팅만 담당, 비즈니스 로직 금지
├── service/         ← 비즈니스 로직 (confirm, cancel, 조회 등)
├── response/        ← 도메인 → JSON 응답 매핑
├── error/           ← 에러 트리거 + 예외 처리
├── auth/            ← 인증 검증
├── domain/          ← 도메인 모델
└── store/           ← 인메모리 저장소
```

**레이어 규칙:**
- `controller/` → `service/`만 호출. 직접 `store/` 접근 금지.
- `service/` → `domain/`, `store/`, `error/` 사용 가능.
- `response/` → 도메인 모델을 PG 공식 스펙 형태의 JSON으로 변환.
- `store/` → 순수 저장/조회만. 비즈니스 로직 금지.

### 에러 처리 패턴

**원칙: 내부 구조는 정돈하되, 외부 응답은 PG 공식 스펙 그대로.**

- 각 PG 모듈에 `ErrorType` enum 정의 — 에러 코드, HTTP 상태, 메시지를 한곳에서 관리.
- PG별 커스텀 예외 클래스 사용 (예: `TossPaymentException`).
- `@RestControllerAdvice`로 예외를 잡아 **PG 공식 에러 응답 포맷**으로 변환하여 반환.
- Mock 전용 에러 래퍼(ApiResponse 등)는 사용하지 않음 — 실제 PG 클라이언트가 파싱할 수 있어야 함.
