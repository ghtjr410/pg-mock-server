# Retry 학습 테스트

Resilience4j Retry의 핵심 동작, 예외 필터, 그리고 CircuitBreaker 조합 시 AOP 순서 함정.

---

## RetryBasicTest

### 첫 시도 실패 → 재시도 성공

```mermaid
sequenceDiagram
    participant Test as 테스트
    participant Retry as Retry
    participant Mock as Mock서버

    Note over Retry: maxAttempts=3<br/>waitDuration=1s

    Test->>Retry: call()
    Retry->>Mock: GET /confirm (1차)
    Note over Mock: DEAD 모드
    Mock-->>Retry: 500 Error

    Note over Retry: 1s 대기

    Note over Mock: NORMAL 모드로 전환
    Retry->>Mock: GET /confirm (2차)
    Mock-->>Retry: 200 OK

    Retry-->>Test: 성공 반환
    Note over Retry: successWithRetry=1
```

### 전부 실패 → 최종 예외 전파

```mermaid
sequenceDiagram
    participant Test as 테스트
    participant Retry as Retry
    participant Mock as Mock서버<br/>(DEAD)

    Note over Retry: maxAttempts=3

    Test->>Retry: call()
    Retry->>Mock: 1차 → 500
    Note over Retry: 대기
    Retry->>Mock: 2차 → 500
    Note over Retry: 대기
    Retry->>Mock: 3차 → 500

    Retry-->>Test: HttpServerErrorException 전파
    Note over Retry: failedWithRetry=1
```

### waitDuration 대기 시간 검증

```mermaid
sequenceDiagram
    participant Test as 테스트
    participant Retry as Retry
    participant Mock as Mock서버<br/>(DEAD)

    Note over Retry: maxAttempts=3<br/>waitDuration=1s

    Note over Test: 시작 시각 기록

    Retry->>Mock: 1차 → 500
    Note over Retry: 1s 대기
    Retry->>Mock: 2차 → 500
    Note over Retry: 1s 대기
    Retry->>Mock: 3차 → 500

    Note over Test: 종료 시각 기록<br/>elapsed >= 2000ms (대기 2회)
```

---

## RetryExceptionFilterTest

### retryExceptions 화이트리스트

```mermaid
sequenceDiagram
    participant Test as 테스트
    participant Retry as Retry
    participant Mock as Mock서버

    Note over Retry: retryExceptions=<br/>[ResourceAccessException]

    rect rgb(230, 255, 230)
        Note over Test: Case 1: 매칭되는 예외
        Test->>Retry: call() → ResourceAccessException
        Note over Retry: retryExceptions에 포함 → 재시도
    end

    rect rgb(255, 230, 230)
        Note over Test: Case 2: 매칭 안 되는 예외
        Test->>Retry: call() → HttpServerErrorException
        Note over Retry: retryExceptions에 없음 → 즉시 실패
        Retry-->>Test: 재시도 없이 예외 전파
        Note over Retry: failedWithoutRetry=1
    end
```

### 비즈니스 에러(403)는 재시도 안 함

```mermaid
sequenceDiagram
    participant Test as 테스트
    participant Retry as Retry
    participant Mock as Mock서버

    Note over Retry: retryExceptions=<br/>[HttpServerErrorException,<br/>ResourceAccessException]

    Test->>Retry: call("reject_company")
    Retry->>Mock: GET /confirm
    Mock-->>Retry: 403 Forbidden
    Note over Retry: HttpClientErrorException(403)<br/>retryExceptions에 없음
    Retry-->>Test: 재시도 없이 즉시 실패
    Note over Retry: 비즈니스 에러를 재시도하면<br/>같은 결과만 반복 → 낭비
```

---

## RetryWithCircuitBreakerTest

Retry와 CircuitBreaker를 조합할 때 데코레이터 순서가 핵심.

### 잘못된 순서: Retry(바깥) → CB(안쪽)

```mermaid
sequenceDiagram
    participant Test as 테스트
    participant Retry as Retry<br/>(바깥)
    participant CB as CircuitBreaker<br/>(안쪽)
    participant Mock as Mock서버<br/>(DEAD)

    Note over Retry: maxAttempts=3

    Test->>Retry: call()
    Retry->>CB: 1차 시도
    CB->>Mock: → 500
    CB->>CB: 실패 +1 (CB 집계: 1)

    Retry->>CB: 2차 시도 (재시도)
    CB->>Mock: → 500
    CB->>CB: 실패 +1 (CB 집계: 2)

    Retry->>CB: 3차 시도 (재시도)
    CB->>Mock: → 500
    CB->>CB: 실패 +1 (CB 집계: 3)

    Note over CB: 논리적 1건 요청이<br/>CB에 3건 실패로 집계!
    Note over CB: 2건 요청 × 3회 재시도<br/>= CB에 6건 실패 기록
```

### 올바른 순서: CB(바깥) → Retry(안쪽)

```mermaid
sequenceDiagram
    participant Test as 테스트
    participant CB as CircuitBreaker<br/>(바깥)
    participant Retry as Retry<br/>(안쪽)
    participant Mock as Mock서버<br/>(DEAD)

    Note over Retry: maxAttempts=3

    Test->>CB: call()
    CB->>Retry: 위임
    Retry->>Mock: 1차 → 500
    Retry->>Mock: 2차 → 500
    Retry->>Mock: 3차 → 500
    Retry-->>CB: 최종 실패 전파

    CB->>CB: 실패 +1 (CB 집계: 1)

    Note over CB: 논리적 1건 = CB 1건
    Note over CB: Retry 내부 재시도는<br/>CB가 모름 → 정확한 실패율
```

### 핵심 원칙

```
CB(바깥) → Retry(안쪽) → 서버
          ↑ 재시도는 CB 안에서 완결
          ↑ CB는 최종 결과만 집계
```
