package com.pgmock.toss.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TossPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder().encodeToString("test_sk_xxxx:".getBytes());

    @Test
    void confirm_정상승인_토스스펙필드() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"tpk_spec","orderId":"ORDER-SPEC","amount":50000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2022-11-16"))
                .andExpect(jsonPath("$.paymentKey").value("tpk_spec"))
                .andExpect(jsonPath("$.type").value("NORMAL"))
                .andExpect(jsonPath("$.orderId").value("ORDER-SPEC"))
                .andExpect(jsonPath("$.orderName").value("주문-ORDER-SPEC"))
                .andExpect(jsonPath("$.mId").value("tosspayments"))
                .andExpect(jsonPath("$.currency").value("KRW"))
                .andExpect(jsonPath("$.method").value("카드"))
                .andExpect(jsonPath("$.totalAmount").value(50000))
                .andExpect(jsonPath("$.balanceAmount").value(50000))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.country").value("KR"))
                .andExpect(jsonPath("$.isPartialCancelable").value(true))
                .andExpect(jsonPath("$.card.issuerCode").value("11"))
                .andExpect(jsonPath("$.card.acquirerCode").value("41"))
                .andExpect(jsonPath("$.card.cardType").value("신용"))
                .andExpect(jsonPath("$.card.ownerType").value("개인"))
                .andExpect(jsonPath("$.card.installmentPlanMonths").value(0))
                .andExpect(jsonPath("$.cancels").isEmpty());
    }

    @Test
    void confirm_인증없으면_401() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"tpk_noauth","orderId":"ORDER-NA","amount":1000}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_KEY"));
    }

    @Test
    void confirm_필수값누락시_400() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":null,"orderId":null,"amount":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void confirm_금액불일치시_400() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_mismatch","orderId":"ORDER-M","amount":10000}
                        """));

        mockMvc.perform(post("/v1/payments/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"tpk_mismatch","orderId":"ORDER-M","amount":99999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
    }

    @Test
    void confirm_멱등키_동일응답() throws Exception {
        String idempotencyKey = "idem-key-001";

        mockMvc.perform(post("/v1/payments/confirm")
                .header("Authorization", AUTH_HEADER)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_idem","orderId":"ORDER-I","amount":30000}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentKey").value("tpk_idem"));

        mockMvc.perform(post("/v1/payments/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"tpk_idem","orderId":"ORDER-I","amount":30000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentKey").value("tpk_idem"));
    }

    @Test
    void confirm_에러트리거_REJECT_CARD_COMPANY() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"tpk_rej","orderId":"ORDER-reject_company-001","amount":5000}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REJECT_CARD_COMPANY"));
    }

    @Test
    void confirm_에러트리거_EXCEED_MAX_AMOUNT() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"tpk_exc","orderId":"ORDER-exceed-001","amount":5000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXCEED_MAX_AMOUNT"));
    }

    @Test
    void confirm_에러트리거_PROVIDER_ERROR() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"tpk_pe","orderId":"ORDER-provider_error-001","amount":5000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROVIDER_ERROR"));
    }

    @Test
    void confirm_에러트리거_SYSTEM_ERROR_500() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"tpk_se","orderId":"ORDER-system_error-001","amount":5000}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FAILED_INTERNAL_SYSTEM_PROCESSING"));
    }

    @Test
    void 결제조회_paymentKey() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_query","orderId":"ORDER-Q","amount":5000}
                        """));

        mockMvc.perform(get("/v1/payments/tpk_query")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.balanceAmount").value(5000));
    }

    @Test
    void 결제조회_orderId() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_oid","orderId":"ORDER-OID-001","amount":8000}
                        """));

        mockMvc.perform(get("/v1/payments/orders/ORDER-OID-001")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentKey").value("tpk_oid"))
                .andExpect(jsonPath("$.orderId").value("ORDER-OID-001"));
    }

    @Test
    void 결제조회_없는건_404() throws Exception {
        mockMvc.perform(get("/v1/payments/nonexistent")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND_PAYMENT"));
    }

    @Test
    void 결제취소_전액() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_cancel","orderId":"ORDER-C","amount":7000}
                        """));

        mockMvc.perform(post("/v1/payments/tpk_cancel/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cancelReason":"고객 요청"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.balanceAmount").value(0))
                .andExpect(jsonPath("$.isPartialCancelable").value(false))
                .andExpect(jsonPath("$.cancels[0].cancelAmount").value(7000))
                .andExpect(jsonPath("$.cancels[0].cancelReason").value("고객 요청"));
    }

    @Test
    void 결제취소_부분취소() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_partial","orderId":"ORDER-P","amount":10000}
                        """));

        // 3000원 부분취소
        mockMvc.perform(post("/v1/payments/tpk_partial/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cancelReason":"부분 환불","cancelAmount":3000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL_CANCELED"))
                .andExpect(jsonPath("$.totalAmount").value(10000))
                .andExpect(jsonPath("$.balanceAmount").value(7000))
                .andExpect(jsonPath("$.isPartialCancelable").value(true))
                .andExpect(jsonPath("$.cancels[0].cancelAmount").value(3000));

        // 나머지 7000원 취소
        mockMvc.perform(post("/v1/payments/tpk_partial/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cancelReason":"나머지 환불","cancelAmount":7000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.balanceAmount").value(0))
                .andExpect(jsonPath("$.cancels.length()").value(2));
    }

    @Test
    void 결제취소_에러트리거_NOT_CANCELABLE() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_nc","orderId":"ORDER-NC","amount":9000}
                        """));

        mockMvc.perform(post("/v1/payments/tpk_nc/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cancelReason":"not_cancelable 사유"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CANCELABLE_PAYMENT"));
    }

    @Test
    void 결제취소_금액초과_403() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_over","orderId":"ORDER-OV","amount":5000}
                        """));

        mockMvc.perform(post("/v1/payments/tpk_over/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cancelReason":"환불","cancelAmount":99999}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CANCELABLE_AMOUNT"));
    }
}
