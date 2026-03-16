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
    void confirm_정상승인() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"tpk_test1","orderId":"ORDER-001","amount":50000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentKey").value("tpk_test1"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.totalAmount").value(50000))
                .andExpect(jsonPath("$.card.issuerCode").value("11"));
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
    void 결제조회_존재하는건() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_query","orderId":"ORDER-Q","amount":5000}
                        """));

        mockMvc.perform(get("/v1/payments/tpk_query")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void 결제조회_없는건_404() throws Exception {
        mockMvc.perform(get("/v1/payments/nonexistent")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND_PAYMENT"));
    }

    @Test
    void 결제취소_정상() throws Exception {
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
                .andExpect(jsonPath("$.cancelReason").value("고객 요청"));
    }
}
