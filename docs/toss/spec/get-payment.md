# 결제 조회

> 공식 문서: https://docs.tosspayments.com/reference#paymentkey로-결제-조회

## 1. paymentKey로 결제 조회

- **Endpoint**: `GET /v1/payments/{paymentKey}`
- **Authentication**: Basic Auth (`Base64({secretKey}:)`)

승인된 결제를 `paymentKey`로 조회합니다. `paymentKey`는 SDK로 결제할 때 발급되는 고유한 키값입니다.

### Path Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| paymentKey | String | O | 결제의 키값. 최대 200자. |

### Response

성공 시 HTTP 200과 함께 [Payment 객체](confirm.md#payment-객체)를 반환합니다.

> 결제 조회 API에서 발생할 수 있는 에러: https://docs.tosspayments.com/reference/error-codes#결제-조회

---

## 2. orderId로 결제 조회

- **Endpoint**: `GET /v1/payments/orders/{orderId}`
- **Authentication**: Basic Auth (`Base64({secretKey}:)`)

승인된 결제를 `orderId`로 조회합니다. `orderId`는 SDK로 결제를 요청할 때 직접 생성한 값입니다.

### Path Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| orderId | String | O | 주문번호. 영문 대소문자, 숫자, `-`, `_`로 이루어진 6~64자. |

### Response

성공 시 HTTP 200과 함께 [Payment 객체](confirm.md#payment-객체)를 반환합니다.

> 결제 조회 API에서 발생할 수 있는 에러: https://docs.tosspayments.com/reference/error-codes#결제-조회
