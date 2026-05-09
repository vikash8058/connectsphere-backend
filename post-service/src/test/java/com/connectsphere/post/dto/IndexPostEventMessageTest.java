package com.connectsphere.post.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexPostEventMessageTest {

    @Test
    void testLombokMethods() {
        IndexPostEventMessage msg1 = IndexPostEventMessage.builder()
                .postId(1)
                .authorId(10)
                .eventType("POST_CREATED")
                .content("Hello")
                .visibility("PUBLIC")
                .build();
        
        IndexPostEventMessage msg2 = IndexPostEventMessage.builder()
                .postId(1)
                .authorId(10)
                .eventType("POST_CREATED")
                .content("Hello")
                .visibility("PUBLIC")
                .build();

        // Covers Getters, Equals, HashCode, and ToString
        assertEquals(msg1, msg2);
        assertEquals(msg1.hashCode(), msg2.hashCode());
        assertEquals("Hello", msg1.getContent());
        assertEquals("PUBLIC", msg1.getVisibility());
        assertNotNull(msg1.toString());
        
        // Covers Setters
        msg1.setEventType("UPDATED");
        assertEquals("UPDATED", msg1.getEventType());
    }

    @Test
    void testEqualsAndHashCode_Complex() {
        IndexPostEventMessage msg1 = IndexPostEventMessage.builder().postId(1).eventType("A").build();
        IndexPostEventMessage msg2 = IndexPostEventMessage.builder().postId(1).eventType("A").build();
        IndexPostEventMessage msg3 = IndexPostEventMessage.builder().postId(2).eventType("A").build();
        IndexPostEventMessage msg4 = IndexPostEventMessage.builder().postId(1).eventType("B").build();
        IndexPostEventMessage msgNull = IndexPostEventMessage.builder().build();
        IndexPostEventMessage msgNull2 = IndexPostEventMessage.builder().build();

        // Equality checks
        assertEquals(msg1, msg1); // Same instance
        assertEquals(msg1, msg2); // Same values
        assertNotEquals(msg3, msg1); // Different postId
        assertNotEquals(msg4, msg1); // Different eventType
        assertNotEquals(null, msg1);
        assertNotEquals(new Object(), msg1);
        assertEquals(msgNull, msgNull2);

        // HashCode checks
        assertEquals(msg1.hashCode(), msg2.hashCode());
        assertNotEquals(msg1.hashCode(), msg3.hashCode());
        
        // Coverage for all fields in toString
        assertNotNull(msg1.toString());
    }
}
