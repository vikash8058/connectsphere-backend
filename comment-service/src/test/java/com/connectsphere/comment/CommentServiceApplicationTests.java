package com.connectsphere.comment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CommentServiceApplicationTests {

	@Test
	void contextLoads() {
        // Simple test to ensure context loads

	}

	@Test
	void main() {
    assertDoesNotThrow(() -> {
        CommentServiceApplication.main(new String[] {});
    });
}

}
