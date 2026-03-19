# 조합 학습 테스트

Bulkhead → CircuitBreaker → Retry 3단 조합의 실제 동작.
개별 2단 조합이 아닌 프로덕션 전체 체인을 검증한다.

---

## FullChainTest

### 3단 조합: 전부 실패 → CB OPEN → 후속 요청 즉시 거절

```mermaid
sequenceDiagram
    participant Test as 테스트
    participant BH as Bulkhead<br/>(maxConcurrent=5)
    participant CB as CircuitBreaker
    participant Retry as Retry<br/>(maxAttempts=3)
    participant Mock as Mock서버<br/>(DEAD)

    rect rgb(255, 230, 230)
        Note over Test: Phase 1: 5건 동시 요청
        Test->>BH: call() × 5
        BH->>CB: 슬롯 확보 → CB 진입
        CB->>Retry: CB 위임

        loop 각 요청당 3회 시도
            Retry->>Mock: → 500
            Retry->>Mock: → 500
            Retry->>Mock: → 500
        end

        Retry-->>CB: 최종 실패 전파
        CB->>CB: 실패 +1 (Retry 재시도는 CB가 모름)
    end

    Note over CB: failedCalls=5<br/>CLOSED → OPEN

    rect rgb(230, 240, 255)
        Note over Test: Phase 2: CB OPEN 후 추가 요청
        Test->>BH: call() × 5
        BH->>CB: 슬롯 확보 → CB 진입
        CB-->>BH: CallNotPermittedException
        Note over CB: Retry 실행 안 됨<br/>서버 도달 안 됨
        BH->>BH: 슬롯 즉시 반환
    end

    Note over BH: availableSlots=5 (전부 가용)
```

### 3단 조합: Retry 성공 → CB 성공 + Bulkhead 정상

```mermaid
sequenceDiagram
    participant Test as 테스트
    participant BH as Bulkhead<br/>(maxConcurrent=5)
    participant CB as CircuitBreaker
    participant Retry as Retry<br/>(maxAttempts=3)
    participant Mock as Mock서버

    Test->>BH: call()
    BH->>CB: 슬롯 확보 → CB 진입
    CB->>Retry: CB 위임

    rect rgb(255, 230, 230)
        Retry->>Mock: 1차 → 500 (DEAD)
        Retry->>Mock: 2차 → 500 (DEAD)
    end

    rect rgb(230, 255, 230)
        Note over Mock: NORMAL 모드 전환
        Retry->>Mock: 3차 → 200 OK
    end

    Retry-->>CB: 최종 성공 전파
    CB->>CB: 성공 +1 (내부 실패 은닉)
    CB-->>BH: 성공 반환
    BH->>BH: 슬롯 반환

    Note over CB: successCalls=1, failedCalls=0
    Note over BH: availableSlots=5 (전부 가용)
```

### 공식 권장 데코레이터 순서

```
Bulkhead(바깥) → CircuitBreaker → Retry(안쪽) → 서버

각 레이어의 역할:
  Bulkhead  : 동시성 제한 — 슬롯 초과 시 즉시 거절
  CB        : 장애 감지 — 실패율 기반 차단
  Retry     : 일시적 장애 복구 — 재시도로 흡수

핵심:
  - Retry 재시도는 CB에 1건으로 집계 (실패율 오염 방지)
  - CB OPEN 시 Retry도 실행 안 됨 (서버 보호)
  - Bulkhead 거절은 CB에 도달하지 않음 (메트릭 오염 방지)
```
