# Bulkhead 학습 테스트

## 이 디렉토리가 증명하는 것

동시 호출 제한과 장애 격리.

### BulkheadBasicTest
- maxConcurrentCalls 초과 → BulkheadFullException (즉시 거절)
- maxWaitDuration > 0 → 대기 후 통과 가능
- Bulkhead 거절 ≠ 서킷 실패 (독립 동작)
