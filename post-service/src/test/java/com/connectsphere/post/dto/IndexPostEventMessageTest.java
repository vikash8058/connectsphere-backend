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
        IndexPostEventMessage msg1 = IndexPostEventMessage.builder().postId(1).build();
        IndexPostEventMessage msg2 = IndexPostEventMessage.builder().postId(1).build();
        IndexPostEventMessage msg3 = IndexPostEventMessage.builder().postId(2).build();

        assertEquals(msg1, msg2);
        assertNotEquals(msg3, msg1);
        assertNotEquals(null, msg1);
        assertNotEquals("not a message", msg1);
        assertEquals(msg1.hashCode(), msg2.hashCode());
    }
}
