package com.connectsphere.payment.controller;

import com.connectsphere.payment.dto.*;
import com.connectsphere.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentResourceTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentResource paymentResource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentResource).build();
    }

    @Test
    void createOrder() throws Exception {
        OrderResponseDTO response = OrderResponseDTO.builder().orderId("order_123").amount(100).build();
        when(paymentService.createOrder(anyInt(), any(OrderRequestDTO.class)))
                .thenReturn(ApiResponseDTO.success("Created", response));

        mockMvc.perform(post("/payments/create-order")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planType\":\"ELITE_MONTHLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("order_123"));
    }

    @Test
    void verifyPayment() throws Exception {
        when(paymentService.verifyPayment(anyInt(), any(PaymentVerificationDTO.class)))
                .thenReturn(ApiResponseDTO.success("Verified", "expiry_date"));

        mockMvc.perform(post("/payments/verify")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razorpayOrderId\":\"order_123\",\"razorpayPaymentId\":\"pay_123\",\"razorpaySignature\":\"sig\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verified"));
    }

    @Test
    void getSubscriptionStatus() throws Exception {
        SubscriptionStatusDTO status = SubscriptionStatusDTO.builder().isElite(true).build();
        when(paymentService.getSubscriptionStatus(anyInt()))
                .thenReturn(ApiResponseDTO.success("Fetched", status));

        mockMvc.perform(get("/payments/subscription-status")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isElite").value(true));
    }
}
