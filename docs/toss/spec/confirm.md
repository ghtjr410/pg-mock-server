# 결제 승인

- **Endpoint**: `POST /v1/payments/confirm`
- **Authentication**: Basic Auth (`Base64({secretKey}:)`)
- **Content-Type**: `application/json`

> 공식 문서: https://docs.tosspayments.com/reference#결제-승인

## Request

### Headers

| 헤더 | 필수 | 설명 |
|------|------|------|
| Authorization | O | `Basic {base64}` — 시크릿 키 뒤에 콜론(`:`)을 붙이고 Base64 인코딩 |
| Idempotency-Key | X | 멱등성 키 — 동일 키로 재요청 시 캐시된 응답 반환 |
| Content-Type | O | `application/json` |

### Body

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| paymentKey | String | O | 결제의 키값. 최대 200자. 중복되지 않는 고유한 값. |
| orderId | String | O | 주문번호. 영문 대소문자, 숫자, `-`, `_`로 이루어진 6~64자 문자열. |
| amount | Number | O | 결제할 금액 |

```json
{
  "paymentKey": "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1",
  "orderId": "a4CWyWY5m89PNh7xJwhk1",
  "amount": 1000
}
```

## Response — Payment 객체

성공 시 HTTP 200과 함께 [Payment 객체](#payment-객체)를 반환합니다. 실패 시 HTTP 상태 코드와 [에러 객체](error-codes.md)를 반환합니다.

> 결제 승인 API에서 발생할 수 있는 에러: https://docs.tosspayments.com/reference/error-codes#결제-승인

---

## Payment 객체

결제 정보를 담고 있는 객체입니다. 결제 승인, 조회, 취소 API 모두 이 객체를 반환합니다.

### 최상위 필드

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| version | String | | Payment 객체의 응답 버전 (e.g. `"2024-06-01"`) |
| paymentKey | String | | 결제의 키값. 최대 200자. |
| type | String | | 결제 타입. `NORMAL`, `BILLING`, `BRANDPAY` 중 하나. |
| orderId | String | | 주문번호. 6~64자. |
| orderName | String | | 구매상품. 최대 100자. (e.g. `"토스 티셔츠 외 2건"`) |
| mId | String | | 상점아이디(MID). 최대 14자. |
| currency | String | | 결제 통화. |
| method | String | O | 결제수단. `카드`, `가상계좌`, `간편결제`, `휴대폰`, `계좌이체`, `문화상품권`, `도서문화상품권`, `게임문화상품권` 중 하나. |
| totalAmount | Number | | 총 결제 금액. 결제 상태가 변해도 최초 결제 금액 유지. |
| balanceAmount | Number | | 취소 가능 잔고. 취소/부분 취소 후 남은 금액. |
| status | String | | 결제 처리 상태. 아래 [상태 값](#상태-값-status) 참조. |
| requestedAt | String | | 결제 요청 시간. ISO 8601 (`yyyy-MM-dd'T'HH:mm:ss±hh:mm`) |
| approvedAt | String | O | 결제 승인 시간. ISO 8601. |
| useEscrow | Boolean | | 에스크로 사용 여부. |
| lastTransactionKey | String | O | 마지막 거래 키값. 최대 64자. 승인/취소 거래 구분용. |
| suppliedAmount | Number | | 공급가액. 취소 시 일부 취소됨. |
| vat | Number | | 부가세. `(amount - taxFreeAmount) / 11` 반올림. |
| cultureExpense | Boolean | | 문화비 지출 여부. 계좌이체/가상계좌만 적용. 카드는 항상 `false`. |
| taxFreeAmount | Number | | 면세 금액. |
| taxExemptionAmount | Integer | | 과세 제외 금액 (컵 보증금 등). |
| cancels | Array | O | 결제 취소 이력. 아래 [Cancels 배열](#cancels-배열-항목) 참조. |
| isPartialCancelable | Boolean | | 부분 취소 가능 여부. `false`이면 전액 취소만 가능. |
| card | Object | O | 카드 결제 정보. 아래 [Card 객체](#card-객체) 참조. |
| virtualAccount | Object | O | 가상계좌 결제 정보. |
| secret | String | O | 웹훅 검증용 값. 최대 50자. |
| mobilePhone | Object | O | 휴대폰 결제 정보. |
| giftCertificate | Object | O | 상품권 결제 정보. |
| transfer | Object | O | 계좌이체 정보. |
| metadata | Object | O | 커스텀 데이터. 최대 5 key-value. 키 최대 40자, 값 최대 2000자. |
| receipt | Object | O | 영수증 정보. `{ "url": "..." }` |
| checkout | Object | O | 결제창 정보. `{ "url": "..." }` |
| easyPay | Object | O | 간편결제 정보. `provider`, `amount`, `discountAmount` 포함. |
| country | String | | 결제 국가. ISO-3166 두 자리 코드. (e.g. `"KR"`) |
| failure | Object | O | 결제 승인 실패 정보. `code`, `message` 포함. |
| cashReceipt | Object | O | 현금영수증 정보. |
| cashReceipts | Array | O | 현금영수증 발행/취소 이력 배열. |
| discount | Object | O | 카드사/퀵계좌이체 즉시 할인 프로모션 정보. `{ "amount": ... }` |

### Card 객체

카드 결제 시 `card` 필드에 포함되는 객체입니다.

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| amount | Number | | 카드사에 결제 요청한 금액. 즉시 할인 금액 포함. |
| issuerCode | String | | 카드 발급사 두 자리 코드. |
| acquirerCode | String | O | 카드 매입사 두 자리 코드. |
| number | String | | 카드번호 (일부 마스킹). 최대 20자. |
| installmentPlanMonths | Integer | | 할부 개월 수. 일시불이면 `0`. |
| approveNo | String | | 카드사 승인 번호. 최대 8자. |
| useCardPoint | Boolean | | 카드사 포인트 사용 여부. |
| cardType | String | | 카드 종류. `신용`, `체크`, `기프트`, `미확인` 중 하나. |
| ownerType | String | | 소유자 타입. `개인`, `법인`, `미확인` 중 하나. |
| acquireStatus | String | | 매입 상태. `READY`, `REQUESTED`, `COMPLETED`, `CANCEL_REQUESTED`, `CANCELED` 중 하나. |
| isInterestFree | Boolean | | 무이자 할부 적용 여부. |
| interestPayer | String | O | 할부 수수료 부담 주체. `BUYER`, `CARD_COMPANY`, `MERCHANT` 중 하나. |

### Cancels 배열 항목

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| cancelAmount | Number | | 취소 금액. |
| cancelReason | String | | 취소 사유. 최대 200자. |
| taxFreeAmount | Number | | 취소된 금액 중 면세 금액. |
| taxExemptionAmount | Integer | | 취소된 금액 중 과세 제외 금액. |
| refundableAmount | Number | | 취소 후 환불 가능 잔액. |
| cardDiscountAmount | Number | | 카드 즉시할인 취소 금액. |
| transferDiscountAmount | Number | | 계좌 결제 즉시할인 취소 금액. |
| easyPayDiscountAmount | Number | | 간편결제 포인트/쿠폰 취소 금액. |
| canceledAt | String | | 취소 시간. ISO 8601. |
| transactionKey | String | | 취소 건 키값. 최대 64자. |
| receiptKey | String | O | 취소 건 현금영수증 키값. 최대 200자. |
| cancelStatus | String | | 취소 상태. `DONE`이면 성공적으로 취소됨. |
| cancelRequestId | String | O | 취소 요청 ID. 비동기 결제 전용. 일반/자동결제에서는 항상 `null`. |

### 상태 값 (status)

| 값 | 설명 |
|----|------|
| `READY` | 결제 생성됨. 인증 전 초기 상태. |
| `IN_PROGRESS` | 결제수단 인증 완료. 결제 승인 API 호출 대기. |
| `WAITING_FOR_DEPOSIT` | 가상계좌 입금 대기. |
| `DONE` | 결제 승인 완료. |
| `CANCELED` | 승인된 결제가 취소됨. |
| `PARTIAL_CANCELED` | 승인된 결제가 부분 취소됨. |
| `ABORTED` | 결제 승인 실패. |
| `EXPIRED` | 결제 유효 시간 30분 경과. `IN_PROGRESS`에서 승인 미호출 시. |

## 공식 응답 예시

```json
{
  "mId": "tosspayments",
  "lastTransactionKey": "9C62B18EEF0DE3EB7F4422EB6D14BC6E",
  "paymentKey": "5EnNZRJGvaBX7zk2yd8ydw26XvwXkLrx9POLqKQjmAw4b0e1",
  "orderId": "a4CWyWY5m89PNh7xJwhk1",
  "orderName": "토스 티셔츠 외 2건",
  "taxExemptionAmount": 0,
  "status": "DONE",
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
  "cancels": null,
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
  "balanceAmount": 1000,
  "suppliedAmount": 909,
  "vat": 91,
  "taxFreeAmount": 0,
  "metadata": null,
  "method": "카드",
  "version": "2024-06-01"
}
```
