# CircuitBreaker 학습 테스트

## 이 디렉토리가 증명하는 것

서킷브레이커의 상태 전이, slowCall 감지, HALF-OPEN 동작,
예외 처리 전략, 그리고 설정 함정 7가지.

### CircuitBreakerBasicTest
- CLOSED → OPEN: 실패율이 threshold를 넘으면 서킷 열림
- OPEN 이후 추가 호출 → CallNotPermittedException
- PARTIAL_FAILURE로 경계값(threshold 전후) 테스트

### CircuitBreakerRecoveryTest
- OPEN → HALF-OPEN → CLOSED: 복구 흐름
- HALF-OPEN → OPEN: 복구 실패 시 다시 차단

### SlowCallDetectionTest
- slowCall은 요청을 끊지 않는다 (timeout과 다름)
- "느린 성공도 장애의 전조" — slowCallRate로 서킷 열림
- slowCall + failure 복합 시나리오

### HalfOpenBehaviorTest
- permittedCalls 초과 요청은 거절
- maxWaitDurationInHalfOpenState: 0(기본) → 무한 대기 위험
- 적절한 값 설정 시 강제 OPEN 복귀

### ExceptionHandlingTest
- 기본: 모든 예외가 실패 → 비즈니스 예외로 서킷 오진
- ignoreExceptions: 특정 예외 무시
- recordExceptions: 특정 예외만 실패 기록
- recordFailurePredicate: 커스텀 판단 로직
- 우선순위: ignoreExceptions > recordExceptions

### ConfigTrapTest — 설정 함정 7가지
각 함정마다 "잘못된 설정 → 올바른 설정" Before/After 증명.
1. minimumNumberOfCalls 기본값 100 → 서킷 안 열림
2. automaticTransition false → 복구 안 됨
3. ignoreExceptions 미설정 → 비즈니스 예외 오진
4. slidingWindowSize 기본값 100 → 감지 느림
5. slowCallDurationThreshold 기본값 60초 → 감지 불가
6. maxWaitDurationInHalfOpenState 기본값 0 → HALF-OPEN 갇힘
