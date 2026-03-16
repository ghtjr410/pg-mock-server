package com.pgmock.toss.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TossPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void confirm_정상승인() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
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
    void confirm_필수값누락시_400() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":null,"orderId":null,"amount":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void confirm_금액불일치시_400() throws Exception {
        // 먼저 정상 승인
        mockMvc.perform(post("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_mismatch","orderId":"ORDER-M","amount":10000}
                        """));

        // 같은 paymentKey로 다른 금액 요청
        mockMvc.perform(post("/v1/payments/confirm")
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
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .content("""
                        {"paymentKey":"tpk_idem","orderId":"ORDER-I","amount":30000}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentKey").value("tpk_idem"));

        // 같은 멱등키로 재요청 — 동일 응답
        mockMvc.perform(post("/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content("""
                                {"paymentKey":"tpk_idem","orderId":"ORDER-I","amount":30000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentKey").value("tpk_idem"));
    }

    @Test
    void 결제조회_존재하는건() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_query","orderId":"ORDER-Q","amount":5000}
                        """));

        mockMvc.perform(get("/v1/payments/tpk_query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void 결제조회_없는건_404() throws Exception {
        mockMvc.perform(get("/v1/payments/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND_PAYMENT"));
    }

    @Test
    void 결제취소_정상() throws Exception {
        mockMvc.perform(post("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"tpk_cancel","orderId":"ORDER-C","amount":7000}
                        """));

        mockMvc.perform(post("/v1/payments/tpk_cancel/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cancelReason":"고객 요청"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").value("고객 요청"));
    }
}
