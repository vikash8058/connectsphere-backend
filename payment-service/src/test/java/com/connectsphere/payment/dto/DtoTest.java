package com.connectsphere.payment.dto;

import com.connectsphere.payment.entity.EliteSubscription;
import com.connectsphere.payment.entity.Transaction;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void testOrderDTOs() {
        OrderRequestDTO or1 = new OrderRequestDTO("PLAN");
        assertEquals("PLAN", or1.getPlanType());
        assertNotNull(new OrderRequestDTO());

        OrderResponseDTO ores1 = OrderResponseDTO.builder().orderId("1").amount(10).currency("INR").keyId("k").build();
        assertEquals("1", ores1.getOrderId());
        assertEquals(10, ores1.getAmount());
        assertNotNull(new OrderResponseDTO());
    }

    @Test
    void testPaymentAndSubscriptionDTOs() {
        PaymentVerificationDTO pv1 = PaymentVerificationDTO.builder().razorpayOrderId("1").razorpayPaymentId("p").razorpaySignature("s").build();
        assertEquals("1", pv1.getRazorpayOrderId());
        assertEquals("p", pv1.getRazorpayPaymentId());
        assertNotNull(new PaymentVerificationDTO());

        LocalDateTime now = LocalDateTime.now();
        SubscriptionStatusDTO ss1 = SubscriptionStatusDTO.builder().isElite(true).planType("E").eliteUntil(now).build();
        assertTrue(ss1.getIsElite());
        assertEquals(now, ss1.getEliteUntil());
        assertNotNull(new SubscriptionStatusDTO());
    }

    @Test
    void testApiAndNotificationDTOs() {
        ApiResponseDTO<String> a1 = new ApiResponseDTO<>(true, "m", "d");
        assertTrue(a1.isSuccess());
        assertEquals("d", a1.getData());
        assertNotNull(new ApiResponseDTO<String>());

        NotificationEventMessage n1 = NotificationEventMessage.builder()
                .type("T").recipientId(1).actorId(2).message("hi")
                .targetId(10).targetType("POST").deepLinkUrl("url")
                .build();
        assertEquals("T", n1.getType());
        assertEquals(1, n1.getRecipientId());
        assertEquals(2, n1.getActorId());
        assertEquals("hi", n1.getMessage());
        assertEquals(10, n1.getTargetId());
        assertEquals("POST", n1.getTargetType());
        assertEquals("url", n1.getDeepLinkUrl());
        assertNotNull(new NotificationEventMessage());
    }

    @Test
    void testEntities() {
        Transaction t1 = Transaction.builder()
                .id(1L).userId(1).amount(BigDecimal.ONE).status("S").planType("P")
                .razorpayOrderId("o").razorpayPaymentId("p").razorpaySignature("sig")
                .currency("INR")
                .build();
        assertEquals(1L, t1.getId());
        assertEquals(1, t1.getUserId());
        assertEquals("o", t1.getRazorpayOrderId());
        assertEquals("p", t1.getRazorpayPaymentId());
        assertEquals("sig", t1.getRazorpaySignature());
        assertEquals(BigDecimal.ONE, t1.getAmount());
        assertEquals("INR", t1.getCurrency());
        
        t1.setCreatedAt(LocalDateTime.now());
        t1.setUpdatedAt(LocalDateTime.now());
        assertNotNull(t1.getCreatedAt());
        assertNotNull(t1.getUpdatedAt());
        assertNotNull(new Transaction());

        EliteSubscription s1 = EliteSubscription.builder().id(1L).userId(1).isActive(true).planType("P").build();
        s1.setStartDate(LocalDateTime.now().minusDays(1));
        s1.setEndDate(LocalDateTime.now().plusDays(1));
        assertEquals(1L, s1.getId());
        assertEquals(1, s1.getUserId());
        assertTrue(s1.getIsActive());
        assertTrue(s1.isCurrentlyActive());
        assertNotNull(new EliteSubscription());
    }
}
