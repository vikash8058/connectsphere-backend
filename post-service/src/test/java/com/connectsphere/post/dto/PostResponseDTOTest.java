package com.connectsphere.post.dto;

import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.Visibility;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PostResponseDTOTest {

    @Test
    void testGettersAndSetters() {
        LocalDateTime now = LocalDateTime.now();
        PostResponseDTO dto = new PostResponseDTO();
        
        dto.setPostId(1);
        dto.setAuthorId(10);
        dto.setContent("Hello World");
        dto.setMediaUrls(List.of("url1", "url2"));
        dto.setPostType(PostType.TEXT);
        dto.setVisibility(Visibility.PUBLIC);
        dto.setLikesCount(5);
        dto.setCommentsCount(2);
        dto.setSharesCount(1);
        dto.setCreatedAt(now);
        dto.setUpdatedAt(now);
        dto.setIsElite(true);

        assertEquals(1, dto.getPostId());
        assertEquals(10, dto.getAuthorId());
        assertEquals("Hello World", dto.getContent());
        assertEquals(2, dto.getMediaUrls().size());
        assertEquals(PostType.TEXT, dto.getPostType());
        assertEquals(Visibility.PUBLIC, dto.getVisibility());
        assertEquals(5, dto.getLikesCount());
        assertEquals(2, dto.getCommentsCount());
        assertEquals(1, dto.getSharesCount());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
        assertTrue(dto.getIsElite());
    }

    @Test
    void testBuilder() {
        PostResponseDTO dto = PostResponseDTO.builder()
                .postId(1)
                .content("Build")
                .build();
        
        assertEquals(1, dto.getPostId());
        assertEquals("Build", dto.getContent());
    }
}
