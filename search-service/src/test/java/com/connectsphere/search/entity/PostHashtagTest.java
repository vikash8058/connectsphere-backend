package com.connectsphere.search.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class PostHashtagTest {

    @Test
    void testGettersSettersAndBuilder() {
        Hashtag hashtag = new Hashtag();
        hashtag.setTag("java");
        
        LocalDateTime now = LocalDateTime.now();
        PostHashtag postHashtag = PostHashtag.builder()
                .id(1)
                .postId(100)
                .hashtag(hashtag)
                .createdAt(now)
                .build();

        assertEquals(1, postHashtag.getId());
        assertEquals(100, postHashtag.getPostId());
        assertEquals(hashtag, postHashtag.getHashtag());
        assertEquals(now, postHashtag.getCreatedAt());
        assertNotNull(postHashtag.toString());
    }

    @Test
    void testNoArgsConstructor() {
        PostHashtag ph = new PostHashtag();
        ph.setPostId(5);
        assertEquals(5, ph.getPostId());
    }
}
