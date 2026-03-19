# 나이스페이먼츠 결제창 Server 승인 모델

## 플로우

1. 클라이언트: JS SDK `AUTHNICE.requestPay()` 호출 → 결제창 노출
2. 결제자: 카드사 인증 완료
3. 나이스페이: `returnUrl`로 인증결과 POST (authResultCode, tid, amount, signature 등)
4. 가맹점 서버: `POST /v1/payments/{tid}` 승인 API 호출

## 승인 API

### 요청

```
POST /v1/payments/{tid}
Host: api.nicepay.co.kr
Authorization: Basic <credentials>
Content-type: application/json;charset=utf-8
```

| Parameter | Type | 필수 | 설명 |
|-----------|------|------|------|
| amount | Integer | O | 결제금액 |
| ediDate | String | | 전문생성일시 (ISO 8601) |
| signData | String | | 위변조 검증 hex(sha256(tid+amount+ediDate+SecretKey)) |

### 응답

```
Content-type: application/json
```

| Parameter | Type | 필수 | 설명 |
|-----------|------|------|------|
| resultCode | String | O | 결과코드 (0000=성공) |
| resultMsg | String | O | 결과메시지 |
| tid | String | O | 결제 승인 키 |
| orderId | String | O | 상점 거래 고유번호 |
| status | String | O | paid/ready/failed/cancelled/partialCancelled/expired |
| amount | Integer | O | 결제 금액 |
| balanceAmt | Integer | O | 취소 가능 잔액 |
| goodsName | String | O | 상품명 |
| payMethod | String | O | card/vbank/bank/cellphone 등 |
| paidAt | String | O | 결제완료시점 (ISO 8601) |
| cancelledAt | String | O | 결제취소시점 |
| approveNo | String | | 승인번호 |
| useEscrow | Boolean | O | 에스크로 여부 |
| currency | String | O | KRW/USD/CNY |
| channel | String | | pc/mobile |

### 카드 객체

| Parameter | Type | 설명 |
|-----------|------|------|
| card.cardCode | String | 카드사 코드 |
| card.cardName | String | 카드사명 |
| card.cardNum | String | 마스킹된 카드번호 |
| card.cardQuota | String | 할부개월 (0=일시불) |
| card.isInterestFree | Boolean | 무이자 여부 |
| card.cardType | String | credit/check |
| card.canPartCancel | String | 부분취소 가능 여부 |
| card.acquCardCode | String | 매입카드사 코드 |
| card.acquCardName | String | 매입카드사명 |

## 취소 API

### 요청

```
POST /v1/payments/{tid}/cancel
Authorization: Basic <credentials>
Content-type: application/json;charset=utf-8
```

| Parameter | Type | 필수 | 설명 |
|-----------|------|------|------|
| reason | String | O | 취소사유 |
| orderId | String | O | 상점 거래 고유번호 |
| cancelAmt | Integer | | 부분취소 금액 (생략시 전액취소) |

### 응답

승인 응답과 동일 구조 + cancels 배열 추가

| Parameter | Type | 설명 |
|-----------|------|------|
| cancels[].tid | String | 취소 거래번호 |
| cancels[].amount | Integer | 취소금액 |
| cancels[].cancelledAt | String | 취소시각 (ISO 8601) |
| cancels[].reason | String | 취소사유 |

## 거래 조회 API

### tid로 조회

```
GET /v1/payments/{tid}
Authorization: Basic <credentials>
```

### orderId로 조회

```
GET /v1/payments/find/{orderId}
Authorization: Basic <credentials>
```

| Parameter | Type | 필수 | 설명 |
|-----------|------|------|------|
| orderDate | String | O | 주문일자 YYYYMMDD |

## 인증

### Basic Auth

```
Authorization: Basic {Base64(clientKey:secretKey)}
```

- clientKey + `:` + secretKey를 Base64 인코딩

## HTTP 상태코드

| Status | 설명 |
|--------|------|
| 200 | 정상 |
| 401 | 인증 실패 |
| 404 | 리소스 없음 |

## 주요 에러코드

| Code | 설명 |
|------|------|
| 0000 | 성공 |
| 3011 | 카드번호 오류 |
| 3095 | 카드사 실패 응답 |
| 3041 | 금액 오류 |
| 2001 | 취소 성공 |
| 2013 | 취소 완료 거래 |
| 2032 | 취소금액 > 취소가능금액 |
| A123 | 거래금액 불일치 |
| U104 | 인증 실패 |
