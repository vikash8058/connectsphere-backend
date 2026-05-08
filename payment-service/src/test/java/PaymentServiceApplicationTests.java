import com.connectsphere.payment.PaymentServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(classes = PaymentServiceApplication.class)
@ActiveProfiles("test")
public class PaymentServiceApplicationTests {
    @Test
    void contextLoads() {
        assertDoesNotThrow(() -> {});
    }
}

