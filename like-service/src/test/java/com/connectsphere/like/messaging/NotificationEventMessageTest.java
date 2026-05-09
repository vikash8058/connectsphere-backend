package com.connectsphere.like.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationEventMessageTest {

    @Test
    void testGettersAndSetters() {
        NotificationEventMessage msg = new NotificationEventMessage();
        msg.setType("LIKE");
        msg.setRecipientId(1);
        msg.setActorId(2);
        msg.setTargetId(101);
        msg.setTargetType("POST");
        msg.setMessage("User 2 liked your post");

        assertEquals("LIKE", msg.getType());
        assertEquals(1, msg.getRecipientId());
        assertEquals(2, msg.getActorId());
        assertEquals(101, msg.getTargetId());
        assertEquals("POST", msg.getTargetType());
        assertEquals("User 2 liked your post", msg.getMessage());
    }

    @Test
    void testBuilder() {
        NotificationEventMessage msg = NotificationEventMessage.builder()
                .type("FOLLOW")
                .build();
        assertEquals("FOLLOW", msg.getType());
    }
}
