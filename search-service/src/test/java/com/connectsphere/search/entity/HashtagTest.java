package com.connectsphere.search.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class HashtagTest {

    @Test
    void testGettersSettersAndBuilder() {
        LocalDateTime now = LocalDateTime.now();
        Hashtag hashtag = Hashtag.builder()
                .hashtagId(1)
                .tag("java")
                .postCount(10)
                .lastUsedAt(now)
                .build();

        assertEquals(1, hashtag.getHashtagId());
        assertEquals("java", hashtag.getTag());
        assertEquals(10, hashtag.getPostCount());
        assertEquals(now, hashtag.getLastUsedAt());
        assertNotNull(hashtag.toString());

        hashtag.setPostCount(11);
        assertEquals(11, hashtag.getPostCount());
    }

    @Test
    void testNoArgsConstructor() {
        Hashtag hashtag = new Hashtag();
        hashtag.setTag("spring");
        assertEquals("spring", hashtag.getTag());
    }
}
