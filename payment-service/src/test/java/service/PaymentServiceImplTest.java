package service;

import com.connectsphere.payment.client.AuthClient;
import com.connectsphere.payment.dto.*;
import com.connectsphere.payment.entity.EliteSubscription;
import com.connectsphere.payment.entity.Transaction;
import com.connectsphere.payment.messaging.NotificationPublisher;
import com.connectsphere.payment.repository.EliteSubscriptionRepository;
import com.connectsphere.payment.repository.TransactionRepository;
import com.connectsphere.payment.service.PaymentServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl Unit Tests")
class PaymentServiceImplTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private EliteSubscriptionRepository subscriptionRepository;
    @Mock private AuthClient authClient;
    @Mock private NotificationPublisher notificationPublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    // ── Test Data Builders ──

    private Transaction buildTransaction(String orderId, String status, String planType) {
        return Transaction.builder()
                .id(1L)
                .userId(42)
                .razorpayOrderId(orderId)
                .razorpayPaymentId(null)
                .razorpaySignature(null)
                .amount(new BigDecimal("199.00"))
                .currency("INR")
                .status(status)
                .planType(planType)
                .build();
    }

    private EliteSubscription buildActiveSubscription(Integer userId, String planType) {
        return EliteSubscription.builder()
                .id(1L)
                .userId(userId)
                .startDate(LocalDateTime.now().minusDays(5))
                .endDate(LocalDateTime.now().plusMonths(1))
                .isActive(true)
                .planType(planType)
                .build();
    }

    private EliteSubscription buildExpiredSubscription(Integer userId) {
        return EliteSubscription.builder()
                .id(1L)
                .userId(userId)
                .startDate(LocalDateTime.now().minusYears(2))
                .endDate(LocalDateTime.now().minusYears(1))  // already expired
                .isActive(true)
                .planType("ELITE_MONTHLY")
                .build();
    }

    // ── createOrder() ────

    @Nested
    @DisplayName("createOrder()")
    class CreateOrderTests {

        @Test
        @DisplayName("Should return error when planType is null")
        void createOrder_nullPlanType_returnsError() {
            // Given
            OrderRequestDTO request = new OrderRequestDTO(null);

            // When
            ApiResponseDTO<OrderResponseDTO> result = paymentService.createOrder(42, request);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Failed to create Razorpay order");

            // No transaction should be saved
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return error when Razorpay client throws exception (bad credentials)")
        void createOrder_razorpayException_returnsError() {
            // Given — blank keys will cause Razorpay to fail
            ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "");
            ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "");

            OrderRequestDTO request = new OrderRequestDTO("ELITE_MONTHLY");

            // When
            ApiResponseDTO<OrderResponseDTO> result = paymentService.createOrder(42, request);

            // Then — error response, no transaction saved
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Failed to create Razorpay order");
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return error when unexpected exception occurs")
        void createOrder_genericException_returnsError() {
            // Plan type is required, so passing a valid one but making Razorpay fail
            ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "invalid");
            OrderRequestDTO request = new OrderRequestDTO("ELITE_MONTHLY");

            ApiResponseDTO<OrderResponseDTO> result = paymentService.createOrder(42, request);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Failed to create Razorpay order");
        }
    }

    // ── verifyPayment() ───

    @Nested
    @DisplayName("verifyPayment()")
    class VerifyPaymentTests {

        /**
         * verifyPayment() calls Utils.verifyPaymentSignature() which is a static Razorpay SDK method.
         * We can test the failure path (transaction not found) and the invalid-signature path
         * by providing a real HMAC signature check that we know will fail.
         */

        @Test
        @DisplayName("Should return error for invalid payment signature")
        void verifyPayment_invalidSignature_returnsError() {
            // Given — deliberately wrong signature
            ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "test_secret_key_min_32_chars_long!");

            PaymentVerificationDTO request = PaymentVerificationDTO.builder()
                    .razorpayOrderId("order_abc123")
                    .razorpayPaymentId("pay_xyz789")
                    .razorpaySignature("invalid_signature_that_will_fail_hmac")
                    .build();

            // When
            ApiResponseDTO<String> result = paymentService.verifyPayment(42, request);

            // Then
            assertThat(result.isSuccess()).isFalse();
            // Could be "Invalid payment signature" or a caught exception message
            assertThat(result.getMessage()).isNotNull();

            // No transaction or subscription should be updated
            verify(transactionRepository, never()).save(any());
            verify(subscriptionRepository, never()).save(any());
            verify(authClient, never()).updateEliteStatus(any(), any(), any());
            verify(notificationPublisher, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should return error when transaction not found for orderId")
        void verifyPayment_transactionNotFound_returnsError() {
            ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "test_secret_key_min_32_chars_long!");

            PaymentVerificationDTO request = PaymentVerificationDTO.builder()
                    .razorpayOrderId("order_notfound")
                    .razorpayPaymentId("pay_xyz")
                    .razorpaySignature("invalid_sig")
                    .build();

            // When
            ApiResponseDTO<String> result = paymentService.verifyPayment(42, request);

            // Then
            assertThat(result.isSuccess()).isFalse();
            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should activate ELITE_MONTHLY subscription on valid payment - existing subscription updated")
        void verifyPayment_monthlyPlan_updatesExistingSubscription() throws Exception {
            // Given — we use a spy on paymentService and mock the static sig verification
            // Since Utils.verifyPaymentSignature is static, we test this path via
            // a valid HMAC-SHA256 signature generated with our test secret

            ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "test_secret_key_min_32_chars_long!");

            String orderId    = "order_monthly123";
            String paymentId  = "pay_monthly456";
            // Generate real HMAC-SHA256 signature so verifyPaymentSignature passes
            String data       = orderId + "|" + paymentId;
            String secret     = "test_secret_key_min_32_chars_long!";
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            String validSignature = bytesToHex(mac.doFinal(data.getBytes()));

            PaymentVerificationDTO request = PaymentVerificationDTO.builder()
                    .razorpayOrderId(orderId)
                    .razorpayPaymentId(paymentId)
                    .razorpaySignature(validSignature)
                    .build();

            Transaction tx = buildTransaction(orderId, "PENDING", "ELITE_MONTHLY");
            when(transactionRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(tx));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);

            EliteSubscription existingSub = buildActiveSubscription(42, "ELITE_MONTHLY");
            when(subscriptionRepository.findByUserId(42)).thenReturn(Optional.of(existingSub));
            when(subscriptionRepository.save(any(EliteSubscription.class))).thenReturn(existingSub);

            when(authClient.updateEliteStatus(eq(42), eq(true), anyString()))
                    .thenReturn(ApiResponseDTO.success("ok", "updated"));

            // When
            ApiResponseDTO<String> result = paymentService.verifyPayment(42, request);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("Payment verified");
            assertThat(result.getData()).isNotNull(); // expiry date string

            // Transaction updated to SUCCESS
            verify(transactionRepository).save(argThat(t ->
                    "SUCCESS".equals(t.getStatus()) &&
                    paymentId.equals(t.getRazorpayPaymentId())
            ));

            // Subscription saved with isActive = true
            verify(subscriptionRepository).save(argThat(s -> s.getIsActive() && Integer.valueOf(42).equals(s.getUserId())));

            // Auth-service called to sync elite status
            verify(authClient).updateEliteStatus(eq(42), eq(true), anyString());

            // Notification published
            verify(notificationPublisher).sendNotification(argThat(n ->
                    "PAYMENT_SUCCESS".equals(n.getType()) && n.getRecipientId().equals(42)
            ));
        }

        @Test
        @DisplayName("Should return error when Razorpay signature verification fails")
        void verifyPayment_invalidSignaturePath() {
            ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "secret");
            PaymentVerificationDTO request = PaymentVerificationDTO.builder()
                    .razorpayOrderId("o").razorpayPaymentId("p").razorpaySignature("invalid").build();

            ApiResponseDTO<String> result = paymentService.verifyPayment(42, request);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("Should return error when unexpected exception occurs in verifyPayment")
        void verifyPayment_genericException_returnsError() {
            // Force an exception by making repository throw
            lenient().when(transactionRepository.findByRazorpayOrderId(anyString()))
                    .thenThrow(new RuntimeException("DB Down"));
            
            PaymentVerificationDTO request = PaymentVerificationDTO.builder()
                    .razorpayOrderId("o").razorpayPaymentId("p").razorpaySignature("s").build();
            
            ApiResponseDTO<String> result = paymentService.verifyPayment(42, request);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("Should create new subscription when user has no existing subscription")
        void verifyPayment_noExistingSubscription_createsNew() throws Exception {
            ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "test_secret_key_min_32_chars_long!");

            String orderId   = "order_yearly999";
            String paymentId = "pay_yearly888";
            String data      = orderId + "|" + paymentId;
            String secret    = "test_secret_key_min_32_chars_long!";
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            String validSignature = bytesToHex(mac.doFinal(data.getBytes()));

            PaymentVerificationDTO request = PaymentVerificationDTO.builder()
                    .razorpayOrderId(orderId)
                    .razorpayPaymentId(paymentId)
                    .razorpaySignature(validSignature)
                    .build();

            Transaction tx = buildTransaction(orderId, "PENDING", "ELITE_YEARLY");
            when(transactionRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(tx));
            when(transactionRepository.save(any())).thenReturn(tx);

            // No existing subscription → new one created
            when(subscriptionRepository.findByUserId(42)).thenReturn(Optional.empty());
            when(subscriptionRepository.save(any())).thenReturn(new EliteSubscription());
            when(authClient.updateEliteStatus(any(), any(), any()))
                    .thenReturn(ApiResponseDTO.success("ok", "updated"));

            // When
            ApiResponseDTO<String> result = paymentService.verifyPayment(42, request);

            // Then
            assertThat(result.isSuccess()).isTrue();
            verify(subscriptionRepository).save(argThat(s ->
                    s.getIsActive() &&
                    "ELITE_YEARLY".equals(s.getPlanType()) &&
                    s.getEndDate().isAfter(LocalDateTime.now().plusMonths(11))  // ~1 year
            ));
        }

        // Helper: byte array → hex string (for HMAC)
        private String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        }
    }

    // ── getSubscriptionStatus() ──
    @Nested
    @DisplayName("getSubscriptionStatus()")
    class GetSubscriptionStatusTests {

        @Test
        @DisplayName("Should return isElite=true for active subscription")
        void getSubscriptionStatus_activeSubscription_returnsElite() {
            // Given
            EliteSubscription sub = buildActiveSubscription(42, "ELITE_MONTHLY");
            when(subscriptionRepository.findByUserId(42)).thenReturn(Optional.of(sub));

            // When
            ApiResponseDTO<SubscriptionStatusDTO> result = paymentService.getSubscriptionStatus(42);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getIsElite()).isTrue();
            assertThat(result.getData().getPlanType()).isEqualTo("ELITE_MONTHLY");
            assertThat(result.getData().getEliteUntil()).isEqualTo(sub.getEndDate());
        }

        @Test
        @DisplayName("Should return isElite=false when subscription is expired")
        void getSubscriptionStatus_expiredSubscription_returnsNotElite() {
            // Given
            EliteSubscription expired = buildExpiredSubscription(42);
            when(subscriptionRepository.findByUserId(42)).thenReturn(Optional.of(expired));

            // When
            ApiResponseDTO<SubscriptionStatusDTO> result = paymentService.getSubscriptionStatus(42);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getIsElite()).isFalse(); // endDate is in the past
        }

        @Test
        @DisplayName("Should return isElite=false when no subscription exists")
        void getSubscriptionStatus_noSubscription_returnsNotElite() {
            // Given
            when(subscriptionRepository.findByUserId(99)).thenReturn(Optional.empty());

            // When
            ApiResponseDTO<SubscriptionStatusDTO> result = paymentService.getSubscriptionStatus(99);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getIsElite()).isFalse();
            assertThat(result.getData().getEliteUntil()).isNull();
            assertThat(result.getData().getPlanType()).isNull();
        }

        @Test
        @DisplayName("Should return isElite=false when isActive flag is false even if endDate is future")
        void getSubscriptionStatus_inactiveFlagSet_returnsNotElite() {
            // Given — manually cancelled subscription
            EliteSubscription sub = EliteSubscription.builder()
                    .id(1L)
                    .userId(42)
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusMonths(1))
                    .isActive(false)  // admin deactivated
                    .planType("ELITE_YEARLY")
                    .build();
            when(subscriptionRepository.findByUserId(42)).thenReturn(Optional.of(sub));

            // When
            ApiResponseDTO<SubscriptionStatusDTO> result = paymentService.getSubscriptionStatus(42);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getIsElite()).isFalse();
        }
    }
}