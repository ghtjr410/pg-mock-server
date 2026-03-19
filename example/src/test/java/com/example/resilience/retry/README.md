# Retry 학습 테스트

## 이 디렉토리가 증명하는 것

Resilience4j Retry의 핵심 동작과 설정 함정.

### RetryBasicTest
- 기본 재시도 동작 (실패 → 대기 → 재시도 → 성공)
- 전부 실패 시 최종 예외 전파
- waitDuration이 실제로 대기하는지 확인

### RetryExceptionFilterTest
- retryExceptions에 포함된 예외만 재시도
- 비즈니스 예외(403)는 재시도 안 함

### RetryWithCircuitBreakerTest
- 설정 함정: AOP 순서가 잘못되면 실패가 곱셈 집계됨
- Retry(바깥) → CB(안쪽): 1건 요청 = CB에 3건 집계 (위험)
- CB(바깥) → Retry(안쪽): 1건 요청 = CB에 1건 집계 (정상)
- 실무 해결: 메서드 분리 또는 aspectOrder 설정
