---
paths:
  - "mock-toss/**"
  - "docs/toss/**"
---

## mock-toss 패키지 구조

```
com.pgmock.toss/
├── controller/   ← 라우팅만 담당 (~60줄)
├── service/      ← 비즈니스 로직 (confirm, cancel, 조회)
├── response/     ← Payment → 토스 공식 JSON 응답 매핑
├── error/        ← 에러 트리거 (orderId/cancelReason 키워드 매칭) + TossPaymentException + @RestControllerAdvice
├── auth/         ← Basic Auth (시크릿 키) 검증
├── domain/       ← Payment 도메인 모델
└── store/        ← ConcurrentHashMap 인메모리 저장소
```

## 응답 포맷

- 성공/에러 응답 모두 **토스페이먼츠 공식 API 스펙**을 그대로 따름.
- 에러 응답: `{ "code": "...", "message": "..." }` (토스 공식 포맷).
- Mock 전용 래퍼로 감싸지 않음 — 실제 토스 클라이언트 코드와 호환되어야 함.

## 카오스 모드

- `GET/PUT /chaos/mode` — NORMAL, SLOW, TIMEOUT, DEAD, PARTIAL_FAILURE 전환 가능.
- 요청 헤더 `X-CHAOS-MODE`로 개별 요청 오버라이드 가능.

## 에러 트리거

- `orderId` 또는 `cancelReason`에 특정 키워드를 포함시켜 에러 응답 유도.

## 토스 스펙 문서

`docs/toss/` — `/fetch-toss-spec` 스킬로 토스 공식 문서에서 가져온 API 스펙 참조.
