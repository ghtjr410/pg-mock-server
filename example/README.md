# Resilience4j 학습 테스트

Mock 서버를 대상으로 Resilience4j의 핵심 동작을 증명하는 테스트 모음.

## 실행

```bash
./gradlew :example:test
```

## TestLogger

테스트 실행 중 Resilience4j 이벤트를 실시간 로깅하는 유틸리티.
자체 `AtomicInteger` 카운터로 누적값을 관리한다 — Resilience4j 내부의 이벤트/메트릭 타이밍 불일치 문제를 우회하기 위함.
(상세: [`circuitbreaker/README.md`](src/test/java/com/example/resilience/circuitbreaker/README.md#testlogger-이벤트-타이밍-문제))

---

## circuitbreaker/

서킷브레이커의 상태 전이, slowCall 감지, HALF-OPEN 동작, 예외 처리, 설정 함정.

### CircuitBreakerBasicTest

| 테스트 | 증명 |
|--------|------|
| DEAD 모드에서 모든 요청 실패시 서킷이 OPEN으로 전환된다 | 실패율 100% → CLOSED→OPEN 전이, 이후 CallNotPermittedException |
| PARTIAL FAILURE 30퍼센트이면 실패율 50퍼센트 미만으로 CLOSED 유지 | 실패율 < threshold → CLOSED 유지 |
| PARTIAL FAILURE 90퍼센트이면 실패율 50퍼센트 초과로 OPEN 전환 | 실패율 > threshold → OPEN 전환 |

### CircuitBreakerRecoveryTest

| 테스트 | 증명 |
|--------|------|
| HALF OPEN에서 성공하면 CLOSED로 복구된다 | OPEN→HALF_OPEN→CLOSED 복구 흐름 |
| HALF OPEN에서 실패하면 OPEN으로 재진입한다 | HALF_OPEN→OPEN 재차단 |

### SlowCallDetectionTest

| 테스트 | 증명 |
|--------|------|
| SLOW 전부이면 slowCallRate 100퍼센트로 OPEN | 느린 응답만으로 서킷 열림 |
| slowCall은 요청을 끊지 않고 느린 성공도 장애 전조로 집계한다 | timeout과 달리 응답은 정상 반환, 집계만 됨 |
| slowCall과 failure 복합시 둘 중 하나라도 threshold 넘으면 OPEN | failureRate와 slowCallRate 독립 판정 |

### HalfOpenBehaviorTest

| 테스트 | 증명 |
|--------|------|
| HALF OPEN에서 permittedNumberOfCalls 초과 요청은 거절된다 | 허용 슬롯 초과 시 CallNotPermittedException |
| maxWaitDurationInHalfOpenState 기본값 0이면 무한 대기 | 느린 요청이 슬롯 점유 시 HALF_OPEN에 갇힘 |
| maxWaitDurationInHalfOpenState 설정시 시간 초과하면 OPEN 복귀 | 타임아웃으로 OPEN 강제 복귀 |

### ExceptionHandlingTest

| 테스트 | 증명 |
|--------|------|
| 기본 동작에서 모든 예외가 실패로 집계되어 비즈니스 에러도 서킷 오진 | 403도 실패 집계 → 서킷 오진 |
| ignoreExceptions 설정시 특정 예외가 집계에서 무시된다 | 비즈니스 예외를 집계에서 제외 |
| recordExceptions에 지정한 예외만 실패로 기록된다 | 화이트리스트 방식 실패 기록 |
| recordFailurePredicate로 커스텀 실패 판단 5xx만 실패 | 조건부 실패 판단 |
| ignoreExceptions가 recordExceptions보다 우선한다 | 양쪽에 있으면 ignore 우선 |

### CircuitBreakerTrapTest

| 테스트 (Before/After 쌍) | 함정 |
|--------------------------|------|
| 함정1: `minimumNumberOfCalls` | 기본값 100 → 실패해도 서킷 안 열림 |
| 함정2: `automaticTransitionFromOpenToHalfOpenEnabled` | 기본값 false → 호출 없으면 OPEN에 영원히 머무름 |
| 함정3: `ignoreExceptions` | 미설정 → 비즈니스 에러가 실패로 오진 |
| 함정4: `slidingWindowSize` | 기본값 100 → 장애 감지 지연 |
| 함정5: `slowCallDurationThreshold` | threshold > readTimeout → slowCall 감지 불가 |
| 함정6: `maxWaitDurationInHalfOpenState` | 기본값 0 → HALF_OPEN 무한 대기 |

---

## retry/

재시도 동작, 예외 필터, CircuitBreaker 조합 시 데코레이터 순서.

### RetryBasicTest

| 테스트 | 증명 |
|--------|------|
| 첫 시도 실패 후 재시도에서 성공한다 | DEAD→NORMAL 전환으로 재시도 성공 |
| DEAD에서 3회 전부 실패하면 최종 예외가 발생한다 | maxAttempts 소진 시 예외 전파 |
| TIMEOUT에서 ResourceAccessException으로 재시도한다 | 네트워크 타임아웃도 재시도 대상 |
| retryExceptions에 없는 예외는 재시도 없이 즉시 실패한다 | 화이트리스트에 없으면 즉시 실패 |
| 비즈니스 에러 403은 재시도하지 않는다 | 비즈니스 에러 재시도 방지 |
| waitDuration 설정시 재시도 간 대기 시간이 적용된다 | 재시도 간 실제 대기 시간 검증 |

### RetryExceptionFilterTest

| 테스트 | 증명 |
|--------|------|
| retryExceptions에 포함된 예외만 재시도 대상이다 | 화이트리스트 방식 재시도 |
| 비즈니스 에러 403은 재시도하지 않는다 | HttpClientErrorException 재시도 제외 |

### RetryWithCircuitBreakerTest

| 테스트 | 증명 |
|--------|------|
| Retry가 바깥이면 1건 요청이 서킷에 3건으로 집계된다 | 잘못된 순서: 재시도마다 CB 실패 누적 |
| CB가 바깥이면 1건 요청이 서킷에 1건으로 집계된다 | 올바른 순서: CB는 최종 결과만 집계 |

---

## bulkhead/

동시 호출 제한과 장애 격리.

### BulkheadBasicTest

| 테스트 | 증명 |
|--------|------|
| 동시 25건중 20건 통과하고 5건 거절된다 | maxConcurrentCalls 초과 → BulkheadFullException |
| maxWaitDuration 0이면 거절된 요청은 즉시 완료된다 | fail-fast: 거절 응답 500ms 이내 |
| maxWaitDuration이 0보다 크면 대기 후 통과한다 | 슬롯 반환 대기 → 거절 없이 전부 통과 |
| Bulkhead 바깥 CB 안쪽이면 거절이 CB 실패로 집계되지 않는다 | Bulkhead 거절은 CB에 도달 안 함 |
