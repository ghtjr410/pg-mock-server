# 결제 취소

- **Endpoint**: `POST /v1/payments/{paymentKey}/cancel`
- **Authentication**: Basic Auth (`Base64({secretKey}:)`)
- **Content-Type**: `application/json`

> 공식 문서: https://docs.tosspayments.com/reference#결제-취소

승인된 결제를 `paymentKey`로 취소합니다. `cancelAmount`에 값을 넣지 않으면 전액 취소됩니다.

멱등키를 요청 헤더에 추가하면 중복 취소 없이 안전하게 처리됩니다.

## Path Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| paymentKey | String | O | 결제의 키값. 최대 200자. |

## Request

### Headers

| 헤더 | 필수 | 설명 |
|------|------|------|
| Authorization | O | `Basic {base64}` |
| Idempotency-Key | X | 멱등성 키 |
| Content-Type | O | `application/json` |

### Body

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| cancelReason | String | O | 취소 사유. 최대 200자. |
| cancelAmount | Number | X | 취소할 금액. 미입력 시 전액 취소. |
| refundReceiveAccount | Object | X | 환불 계좌 정보. **가상계좌 결제에만 필수.** |
| refundReceiveAccount.bank | String | 조건부 | 은행 코드. |
| refundReceiveAccount.accountNumber | String | 조건부 | 계좌번호. `-` 없이 숫자만. 최대 20자. |
| refundReceiveAccount.holderName | String | 조건부 | 예금주. 최대 60자. |
| taxFreeAmount | Number | X | 취소할 금액 중 면세 금액. 기본값 `0`. 면세/복합과세 상점에만 적용. |
| currency | String | X | 취소 통화. 승인된 통화와 동일해야 함. `KRW`, `USD`, `JPY` 지원. |
| refundableAmount | Number | X | ~~현재 환불 가능한 금액.~~ **DEPRECATED** — 멱등키 사용 권장. |

```json
{
  "cancelReason": "구매자 변심",
  "cancelAmount": 1000
}
```

## Response

성공 시 HTTP 200과 함께 [Payment 객체](confirm.md#payment-객체)를 반환합니다.
`cancels` 필드에 취소 객체가 배열로 포함됩니다. 부분 취소를 여러 번 하면 취소 객체가 여러 개 돌아옵니다.

### 취소 후 변경되는 필드

| 필드 | 설명 |
|------|------|
| status | `CANCELED` (전액 취소) 또는 `PARTIAL_CANCELED` (부분 취소) |
| balanceAmount | 취소 후 남은 잔액 |
| suppliedAmount | 취소 후 공급가액 |
| vat | 취소 후 부가세 |
| isPartialCancelable | 추가 부분 취소 가능 여부 |
| cancels | 취소 내역 배열 |
| lastTransactionKey | 취소 거래의 트랜잭션 키 |

### Cancels 배열 항목

전체 필드는 [confirm.md — Cancels 배열 항목](confirm.md#cancels-배열-항목) 참조.

실패 시 HTTP 상태 코드와 에러 객체를 반환합니다.

> 결제 취소 API에서 발생할 수 있는 에러: https://docs.tosspayments.com/reference/error-codes#결제-취소

## 공식 응답 예시 (전액 취소)

```json
{
  "mId": "tosspayments",
  "lastTransactionKey": "090A796806E726BBB929F4A2CA7DB9A7",
  "paymentKey": "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1",
  "orderId": "a4CWyWY5m89PNh7xJwhk1",
  "orderName": "토스 티셔츠 외 2건",
  "taxExemptionAmount": 0,
  "status": "CANCELED",
  "requestedAt": "2024-02-13T12:17:57+09:00",
  "approvedAt": "2024-02-13T12:18:14+09:00",
  "useEscrow": false,
  "cultureExpense": false,
  "card": {
    "issuerCode": "71",
    "acquirerCode": "71",
    "number": "12345678****000*",
    "installmentPlanMonths": 0,
    "isInterestFree": false,
    "interestPayer": null,
    "approveNo": "00000000",
    "useCardPoint": false,
    "cardType": "신용",
    "ownerType": "개인",
    "acquireStatus": "READY",
    "amount": 1000
  },
  "virtualAccount": null,
  "transfer": null,
  "mobilePhone": null,
  "giftCertificate": null,
  "cashReceipt": null,
  "cashReceipts": null,
  "discount": null,
  "cancels": [
    {
      "transactionKey": "090A796806E726BBB929F4A2CA7DB9A7",
      "cancelReason": "테스트 결제 취소",
      "taxExemptionAmount": 0,
      "canceledAt": "2024-02-13T12:20:23+09:00",
      "transferDiscountAmount": 0,
      "easyPayDiscountAmount": 0,
      "receiptKey": null,
      "cancelAmount": 1000,
      "taxFreeAmount": 0,
      "refundableAmount": 0,
      "cancelStatus": "DONE",
      "cancelRequestId": null
    }
  ],
  "secret": null,
  "type": "NORMAL",
  "easyPay": {
    "provider": "토스페이",
    "amount": 0,
    "discountAmount": 0
  },
  "country": "KR",
  "failure": null,
  "isPartialCancelable": true,
  "receipt": {
    "url": "https://dashboard.tosspayments.com/receipt/redirection?transactionId=tviva20240213121757MvuS8&ref=PX"
  },
  "checkout": {
    "url": "https://api.tosspayments.com/v1/payments/5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1/checkout"
  },
  "currency": "KRW",
  "totalAmount": 1000,
  "balanceAmount": 0,
  "suppliedAmount": 0,
  "vat": 0,
  "taxFreeAmount": 0,
  "method": "카드",
  "version": "2024-06-01",
  "metadata": null
}
```
