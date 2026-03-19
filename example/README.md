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
| PARTIAL FAILURE 30퍼센트이면 실패율 50퍼센트 미만으로 CLOSED 유지 | slidingWindowSize=100으로 확률적 편차 억제 |
| PARTIAL FAILURE 90퍼센트이면 실패율 50퍼센트 초과로 OPEN 전환 | 실패율 > threshold → OPEN 전환 |
| recordResult로 예외 없는 응답도 실패로 집계할 수 있다 | 200 OK + IN_PROGRESS → 결과값 기반 실패 판정 |
| recordResult 조건 불일치시 성공으로 집계된다 | DONE 응답 → predicate 불일치 → 성공 |

### CircuitBreakerRecoveryTest

| 테스트 | 증명 |
|--------|------|
| HALF OPEN에서 성공하면 CLOSED로 복구된다 | OPEN→HALF_OPEN→CLOSED 복구 흐름 |
| HALF OPEN에서 실패하면 OPEN으로 재진입한다 | HALF_OPEN→OPEN 재차단 |
| CLOSED 복구 후 슬라이딩 윈도우가 초기화되어 이전 실패가 이월되지 않는다 | 복구 후 40% < 50% → CLOSED 유지 |
| HALF OPEN에서 실패율이 threshold 미만이면 CLOSED로 복구된다 | 33% < 50% → CLOSED |
| HALF OPEN에서 실패율이 threshold 이상이면 OPEN으로 재진입한다 | 66% > 50% → OPEN |

### SlowCallDetectionTest

| 테스트 | 증명 |
|--------|------|
| SLOW 전부이면 slowCallRate 100퍼센트로 OPEN | 느린 응답만으로 서킷 열림 |
| slowCall은 요청을 끊지 않고 느린 성공도 장애 전조로 집계한다 | timeout과 달리 응답은 정상 반환, 집계만 됨 |
| slowCall과 failure 복합시 둘 중 하나라도 threshold 넘으면 OPEN | failureRate와 slowCallRate 독립 판정 |
| HALF OPEN에서 느린 성공만으로도 slowCallRate 초과시 OPEN 재진입한다 | 성공해도 느리면 복구 안 됨 |

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

### TimeBasedWindowTest

| 테스트 | 증명 |
|--------|------|
| TIME BASED 윈도우에서 시간 경과 후 이전 실패가 만료된다 | COUNT_BASED와의 핵심 차이: 시간 경과 시 오래된 실패 자동 폐기 |

### SpecialStateTest

| 테스트 | 증명 |
|--------|------|
| FORCED OPEN 상태에서는 모든 요청이 거부된다 | 수동 서킷 차단 — PG 점검 시나리오 |
| DISABLED 상태에서는 서킷이 항상 허용하고 상태 전이가 없다 | 서킷 비활성화 — 차단/메트릭 없음 |
| METRICS ONLY 상태에서는 메트릭만 수집하고 차단하지 않는다 | 프로덕션 검증용 — 차단 없이 메트릭만 |

---

## retry/

재시도 동작, 예외 필터, 결과 기반 재시도, CircuitBreaker 조합.

### RetryBasicTest

| 테스트 | 증명 |
|--------|------|
| 첫 시도 실패 후 재시도에서 성공한다 | DEAD→NORMAL 전환으로 재시도 성공 |
| DEAD에서 3회 전부 실패하면 최종 예외가 발생한다 | maxAttempts 소진 시 예외 전파 |
| TIMEOUT에서 ResourceAccessException으로 재시도한다 | 네트워크 타임아웃도 재시도 대상 |
| retryExceptions에 없는 예외는 재시도 없이 즉시 실패한다 | 화이트리스트에 없으면 즉시 실패 |
| 비즈니스 에러 403은 재시도하지 않는다 | 비즈니스 에러 재시도 방지 |
| waitDuration 설정시 재시도 간 대기 시간이 적용된다 | 재시도 간 실제 대기 시간 검증 |
| maxAttempts는 초기 호출을 포함한 총 시도 횟수이다 | maxAttempts(3) = 초기 1 + 재시도 2 = 총 3회 |
| failAfterMaxAttempts는 예외 기반 재시도에는 적용되지 않는다 | true로 설정해도 원래 예외 전파 |
| failAfterMaxAttempts true 결과 기반에서 MaxRetriesExceededException이 발생한다 | 결과 기반에서만 래핑 예외 발생 |

### RetryExceptionFilterTest

| 테스트 | 증명 |
|--------|------|
| retryExceptions에 포함된 예외만 재시도 대상이다 | 화이트리스트 방식 재시도 |
| 비즈니스 에러 403은 재시도하지 않는다 | HttpClientErrorException 재시도 제외 |

### RetryBackoffTest

| 테스트 | 증명 |
|--------|------|
| exponentialBackoff 적용시 대기시간이 지수적으로 증가한다 | 1초→2초→4초 대기 간격 검증 |
| exponentialRandomBackoff 적용시 대기시간이 jitter 범위 안에 있다 | thundering herd 방지 — 랜덤 범위 검증 |

### RetryResultPredicateTest

| 테스트 | 증명 |
|--------|------|
| 응답 status가 IN PROGRESS이면 예외 없이도 재시도한다 | HTTP 200 + 비즈니스 미완료 → 결과 기반 재시도 |
| 모든 시도에서 predicate 매칭시 마지막 결과가 반환된다 | 예외가 아닌 마지막 결과 반환 (retryExceptions와의 차이) |
| retryOnResult와 retryExceptions를 함께 사용하면 둘 다 재시도 트리거된다 | 예외 + 결과값 복합 재시도 |

### RetryWithCircuitBreakerTest

| 테스트 | 증명 |
|--------|------|
| Retry가 바깥이면 1건 요청이 서킷에 3건으로 집계된다 | 잘못된 순서: 재시도마다 CB 실패 누적 |
| CB가 바깥이면 1건 요청이 서킷에 1건으로 집계된다 | 올바른 순서: CB는 최종 결과만 집계 |
| CB가 바깥이면 Retry 내부 실패 후 최종 성공은 CB에 성공 1건으로 집계된다 | Retry 내부 실패가 CB를 오염시키지 않음 |

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
| 슬롯 반환 후 대기 중이던 요청이 통과한다 | 세마포어 슬롯 풀 반환 메커니즘 명시적 검증 |
| CB 바깥 Bulkhead 안쪽이면 거절이 CB 실패로 집계된다 | 잘못된 순서: 동시성 포화가 장애로 오인 |
| CB OPEN 상태에서 Bulkhead 슬롯은 잡았다가 즉시 반환된다 | CB 거절이 동기적 → 슬롯 점유 무해 |

---

## combination/

Bulkhead → CB → Retry 3단 조합 통합 테스트.

### FullChainTest

| 테스트 | 증명 |
|--------|------|
| Bulkhead CB Retry 3단 조합에서 각 레이어가 올바르게 동작한다 | 전부 실패 → CB OPEN → 후속 즉시 거절, Retry 재시도는 CB 1건 |
| 삼단 조합에서 Retry 성공시 CB 성공 집계되고 Bulkhead 슬롯 반환된다 | Retry가 실패 흡수 → CB/Bulkhead 모두 영향 없음 |

---

## timeout/

네트워크 타임아웃의 기본 동작.

### TimeoutTest

| 테스트 | 증명 |
|--------|------|
| TIMEOUT에서 readTimeout 초과시 ResourceAccessException 발생 | 서버 무응답 → readTimeout 보호 |
| SLOW 응답이 readTimeout 이내이면 성공한다 | 느려도 timeout 이내면 정상 |
| SLOW 응답이 readTimeout을 초과하면 ResourceAccessException 발생 | readTimeout < 응답시간 → 실패 |
