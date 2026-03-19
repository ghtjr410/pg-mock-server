# Mock 충실도 원칙

Mock 서버의 목적은 **실제 PG 클라이언트 코드로 장애 대응 테스트**를 하는 것이다.
판단 기준: **"클라이언트 코드가 파싱하거나 분기 조건으로 쓰는가?"**

## 공식 스펙과 동일하게 유지 (High Fidelity)

클라이언트가 직접 의존하는 항목. 차이가 있으면 테스트 자체가 무의미해진다.

| 항목 | 이유 |
|------|------|
| 응답 JSON 필드명 + 구조 | 클라이언트가 역직렬화함 |
| `resultCode` 값 | 성공/실패 분기 조건 (0000 = 성공) |
| HTTP 상태 코드 | 4xx vs 5xx로 재시도 여부 판단 |
| `status` 상태값 | `paid`, `cancelled` 등 상태 머신 로직 |
| API 경로 (`/v1/payments/{tid}`) | 연결 자체가 안 됨 |
| 인증 방식 (`Basic clientKey:secretKey`) | 토스(`secretKey:`)와 다름 |
| `balanceAmt` 계산 | 부분 취소 로직 검증 |
| 에러 응답 포맷 (`resultCode`/`resultMsg`) | 토스(`code`/`message`)와 다름 |

## 러프하게 허용 (Low Fidelity)

클라이언트가 무시하거나 저장만 하는 항목. 고정값/랜덤으로 충분하다.

| 항목 | Mock 처리 | 이유 |
|------|-----------|------|
| 카드 상세 (cardCode, cardName, cardNum) | 고정값 (KB국민) | 보통 저장만, 분기 없음 |
| receiptUrl | mock URL | 유효할 필요 없음 |
| 에러 `resultMsg` 텍스트 | 축약 허용 | 로깅용, 코드 분기에 안 씀 |
| goodsName, orderId | 자동 생성 | 표시용 |
| signature | `null` | 위변조 검증 Mock 불필요 |
| 항상 null인 필드 (bank, vbank, cashReceipts 등) | `null` | 카드 외 미지원 |
| approveNo | 랜덤 8자리 | 고유하면 충분 |
