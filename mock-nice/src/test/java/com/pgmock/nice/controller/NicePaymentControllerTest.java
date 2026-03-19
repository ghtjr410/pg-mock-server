package com.pgmock.nice.controller;

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
class NicePaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String AUTH_HEADER = "Basic " +
            Base64.getEncoder().encodeToString("testClientKey:testSecretKey".getBytes());

    @Test
    void 승인_정상_나이스스펙필드() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_test001")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("0000"))
                .andExpect(jsonPath("$.resultMsg").value("정상 처리되었습니다."))
                .andExpect(jsonPath("$.tid").value("nicuntct_test001"))
                .andExpect(jsonPath("$.orderId").value("ORDER-nicuntct_test001"))
                .andExpect(jsonPath("$.status").value("paid"))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.balanceAmt").value(50000))
                .andExpect(jsonPath("$.payMethod").value("card"))
                .andExpect(jsonPath("$.currency").value("KRW"))
                .andExpect(jsonPath("$.useEscrow").value(false))
                .andExpect(jsonPath("$.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.failedAt").value("0"))
                .andExpect(jsonPath("$.approveNo").isNotEmpty())
                .andExpect(jsonPath("$.card.cardCode").value("02"))
                .andExpect(jsonPath("$.card.cardName").value("KB국민"))
                .andExpect(jsonPath("$.card.cardType").value("credit"))
                .andExpect(jsonPath("$.card.canPartCancel").value("true"))
                .andExpect(jsonPath("$.cancels").isEmpty())
                .andExpect(jsonPath("$.receiptUrl").isNotEmpty());
    }

    @Test
    void 승인_인증없으면_401() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_noauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("U104"));
    }

    @Test
    void 승인_잘못된인증_401() throws Exception {
        String badAuth = "Basic " + Base64.getEncoder().encodeToString("noColonKey".getBytes());
        mockMvc.perform(post("/v1/payments/nicuntct_badauth")
                        .header("Authorization", badAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("U104"));
    }

    @Test
    void 승인_금액0이하_400() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_zero")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("9000"));
    }

    @Test
    void 승인_금액불일치_400() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_mismatch")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":10000}
                        """));

        mockMvc.perform(post("/v1/payments/nicuntct_mismatch")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":99999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("A123"));
    }

    @Test
    void 승인_에러트리거_카드오류() throws Exception {
        mockMvc.perform(post("/v1/payments/ORDER-card_error-001")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":5000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("3011"));
    }

    @Test
    void 승인_에러트리거_시스템오류() throws Exception {
        mockMvc.perform(post("/v1/payments/ORDER-system_error-001")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":5000}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.resultCode").value("9002"));
    }

    @Test
    void 거래조회_tid() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_query")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":5000}
                        """));

        mockMvc.perform(get("/v1/payments/nicuntct_query")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paid"))
                .andExpect(jsonPath("$.balanceAmt").value(5000));
    }

    @Test
    void 거래조회_orderId() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_oid")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":8000}
                        """));

        mockMvc.perform(get("/v1/payments/find/ORDER-nicuntct_oid")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tid").value("nicuntct_oid"))
                .andExpect(jsonPath("$.orderId").value("ORDER-nicuntct_oid"));
    }

    @Test
    void 거래조회_없는건_404() throws Exception {
        mockMvc.perform(get("/v1/payments/nonexistent_tid")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("A118"));
    }

    @Test
    void 결제취소_전액() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_cancel")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":7000}
                        """));

        mockMvc.perform(post("/v1/payments/nicuntct_cancel/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"고객 요청","orderId":"ORDER-nicuntct_cancel"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("0000"))
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.balanceAmt").value(0))
                .andExpect(jsonPath("$.cancelledTid").isNotEmpty())
                .andExpect(jsonPath("$.cancels[0].amount").value(7000))
                .andExpect(jsonPath("$.cancels[0].reason").value("고객 요청"));
    }

    @Test
    void 결제취소_부분취소() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_partial")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":10000}
                        """));

        mockMvc.perform(post("/v1/payments/nicuntct_partial/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"부분 환불","orderId":"ORDER-nicuntct_partial","cancelAmt":3000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("partialCancelled"))
                .andExpect(jsonPath("$.amount").value(10000))
                .andExpect(jsonPath("$.balanceAmt").value(7000))
                .andExpect(jsonPath("$.cancels[0].amount").value(3000));

        mockMvc.perform(post("/v1/payments/nicuntct_partial/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"나머지 환불","orderId":"ORDER-nicuntct_partial","cancelAmt":7000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.balanceAmt").value(0))
                .andExpect(jsonPath("$.cancels.length()").value(2));
    }

    @Test
    void 결제취소_이미취소된거래_400() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_already")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":5000}
                        """));

        mockMvc.perform(post("/v1/payments/nicuntct_already/cancel")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"전액취소","orderId":"ORDER-nicuntct_already"}
                        """));

        mockMvc.perform(post("/v1/payments/nicuntct_already/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"또 취소","orderId":"ORDER-nicuntct_already"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("2013"));
    }

    @Test
    void 결제취소_금액초과_403() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_over")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":5000}
                        """));

        mockMvc.perform(post("/v1/payments/nicuntct_over/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"환불","orderId":"ORDER-nicuntct_over","cancelAmt":99999}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("2032"));
    }

    @Test
    void 결제취소_에러트리거() throws Exception {
        mockMvc.perform(post("/v1/payments/nicuntct_trig")
                .header("Authorization", AUTH_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":9000}
                        """));

        mockMvc.perform(post("/v1/payments/nicuntct_trig/cancel")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"cancel_fail 사유","orderId":"ORDER-nicuntct_trig"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("2003"));
    }
}
