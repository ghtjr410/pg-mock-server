# Mock Payment 응답 — 공식 스펙과의 차이점

Mock 서버는 **카드 결제**만 지원합니다. 응답 JSON 구조는 공식 스펙과 동일하지만, 일부 필드가 고정값이거나 미구현(`null`)입니다.

## 고정값 필드

| 필드 | Mock 값 | 공식 스펙 |
|------|---------|-----------|
| payMethod | `"card"` | card/vbank/bank/cellphone 등 |
| currency | `"KRW"` | KRW/USD/CNY |
| channel | `"pc"` | pc/mobile |
| useEscrow | `false` | 에스크로 설정에 따라 |
| goodsName | `"상품-{tid}"` | 가맹점이 결제창에서 설정 |
| orderId | `"ORDER-{tid}"` | 가맹점이 결제창에서 설정 |
| failedAt | `"0"` | 실패 시각 (실패 아닌 경우 0) |

## Card 객체 고정값

| 필드 | Mock 값 |
|------|---------|
| cardCode | `"02"` (KB국민) |
| cardName | `"KB국민"` |
| cardNum | `"536112******1234"` |
| cardQuota | `"0"` (일시불) |
| isInterestFree | `false` |
| cardType | `"credit"` |
| canPartCancel | `"true"` |
| acquCardCode | `"02"` |
| acquCardName | `"KB국민"` |

## 미구현 필드 (항상 `null`)

| 필드 | 사유 |
|------|------|
| bank | 계좌이체 미지원 |
| vbank | 가상계좌 미지원 |
| cashReceipts | 현금영수증 미지원 |
| coupon | 즉시할인 미지원 |
| signature | 위변조 검증 미구현 |
| mallReserved | 예비필드 미구현 |
| mallUserId | 사용자 ID 미구현 |
| buyerName/buyerTel/buyerEmail | 구매자 정보 미구현 |

## Cancels 배열

Mock의 cancels 항목에 포함되는 필드:

| 필드 | 설명 |
|------|------|
| tid | 취소 거래번호 (`{원거래tid}-cancel-{순번}`) |
| amount | 취소금액 |
| cancelledAt | 취소시각 (ISO 8601) |
| reason | 취소사유 |
| receiptUrl | 취소 매출전표 URL |
| couponAmt | 쿠폰 취소금액 (항상 0) |

## 공식 대비 생략된 응답 필드

Mock에서 생략된 공식 응답 필드:

| 필드 | 공식 스펙 설명 |
|------|---------------|
| ediDate | 응답전문생성일시 |
| signData | 위변조 검증 데이터 |
| issuedCashReceipt | 현금영수증 발급여부 |
| returnCharSet | 인코딩 방식 |
