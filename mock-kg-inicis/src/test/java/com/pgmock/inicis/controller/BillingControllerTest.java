package com.pgmock.inicis.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 빌링결제_정상() throws Exception {
        mockMvc.perform(post("/api/v1/billing/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "billingKey":"blk_test1",
                                  "orderId":"ORDER-B1",
                                  "amount":30000,
                                  "productName":"월간 구독",
                                  "buyerName":"홍길동"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("00"))
                .andExpect(jsonPath("$.resultMsg").value("정상"))
                .andExpect(jsonPath("$.tid").isNotEmpty())
                .andExpect(jsonPath("$.orderId").value("ORDER-B1"))
                .andExpect(jsonPath("$.amount").value(30000));
    }

    @Test
    void 빌링결제_필수값누락_400() throws Exception {
        mockMvc.perform(post("/api/v1/billing/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"billingKey":null,"orderId":null,"amount":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("E001"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 거래조회_정상() throws Exception {
        // 먼저 결제
        MvcResult result = mockMvc.perform(post("/api/v1/billing/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "billingKey":"blk_inq","orderId":"ORDER-INQ",
                                  "amount":15000,"productName":"조회테스트","buyerName":"테스트"
                                }
                                """))
                .andReturn();

        Map<String, Object> payResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        String tid = (String) payResponse.get("tid");

        // 조회
        mockMvc.perform(post("/api/v1/billing/inquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tid\":\"" + tid + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("00"))
                .andExpect(jsonPath("$.tid").value(tid));
    }

    @Test
    void 거래조회_없는건() throws Exception {
        mockMvc.perform(post("/api/v1/billing/inquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tid":"NONEXISTENT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("E001"));
    }
}
