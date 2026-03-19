# Mock Payment 응답 — 공식 스펙과의 차이점

Mock 서버는 **카드 결제**만 지원합니다. 응답 JSON 구조는 공식 스펙과 동일하지만, 일부 필드가 고정값이거나 미구현(`null`)입니다.

## 고정값 필드

| 필드 | Mock 값 | 공식 스펙 |
|------|---------|-----------|
| version | `"2022-11-16"` | `"2024-06-01"` (최신) |
| type | `"NORMAL"` | `NORMAL`, `BILLING`, `BRANDPAY` |
| method | `"카드"` | 8가지 결제수단 |
| currency | `"KRW"` | 다양한 통화 |
| country | `"KR"` | ISO-3166 |
| mId | `"tosspayments"` | 상점별 발급 |
| orderName | `"주문-{orderId}"` | 가맹점이 설정 |
| useEscrow | `false` | 에스크로 설정에 따라 |
| cultureExpense | `false` | 결제수단에 따라 |
| isPartialCancelable | `true` (잔액 있을 때) | 결제수단에 따라 |

## Card 객체 고정값

| 필드 | Mock 값 |
|------|---------|
| issuerCode | `"11"` |
| acquirerCode | `"41"` |
| number | `"1234-****-****-5678"` |
| installmentPlanMonths | `0` |
| isInterestFree | `false` |
| interestPayer | `null` |
| approveNo | 8자리 랜덤 숫자 |
| useCardPoint | `false` |
| cardType | `"신용"` |
| ownerType | `"개인"` |
| acquireStatus | `"READY"` |

## 미구현 필드 (항상 `null`)

| 필드 | 사유 |
|------|------|
| virtualAccount | 가상계좌 미지원 |
| transfer | 계좌이체 미지원 |
| mobilePhone | 휴대폰 결제 미지원 |
| giftCertificate | 상품권 미지원 |
| cashReceipt | 현금영수증 미지원 |
| cashReceipts | 현금영수증 미지원 |
| discount | 즉시 할인 미지원 |
| easyPay | 간편결제 미지원 |
| failure | 승인 성공 시 항상 null |
| metadata | 커스텀 데이터 미지원 |
| secret | 웹훅 미지원 |

## Cancels 배열 — 공식 대비 누락 필드

Mock의 cancels 항목에는 아래 필드가 포함되지 않습니다:

| 필드 | 공식 스펙 설명 |
|------|---------------|
| taxFreeAmount | 취소된 면세 금액 |
| taxExemptionAmount | 취소된 과세 제외 금액 |
| cardDiscountAmount | 카드 즉시할인 취소 금액 |
| transferDiscountAmount | 계좌 즉시할인 취소 금액 |
| easyPayDiscountAmount | 간편결제 할인 취소 금액 |
| receiptKey | 현금영수증 키 |
| cancelStatus | 취소 상태 |
| cancelRequestId | 취소 요청 ID |

## 금액 계산

| 필드 | Mock 계산 방식 | 공식 계산 방식 |
|------|---------------|---------------|
| suppliedAmount | `amount / 1.1` 반올림 | `(amount - taxFreeAmount) / 11` 반올림 |
| vat | `totalAmount - suppliedAmount` | 동일 |
| taxFreeAmount | 항상 `0` | 면세/복합과세 상점에 따라 |
| taxExemptionAmount | 항상 `0` | 과세 제외 금액에 따라 |

## receipt / checkout

| 필드 | Mock 값 |
|------|---------|
| receipt.url | `"https://mock-receipt..."` |
| checkout.url | `"https://mock-checkout..."` |
