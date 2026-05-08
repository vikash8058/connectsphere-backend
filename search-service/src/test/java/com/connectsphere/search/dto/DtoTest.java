package com.connectsphere.search.dto;

import com.connectsphere.search.entity.Hashtag;
import com.connectsphere.search.entity.PostHashtag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void testIndexPostEventMessage() {
        IndexPostEventMessage msg = IndexPostEventMessage.builder()
                .eventType("POST_CREATED")
                .postId(1)
                .authorId(2)
                .content("content")
                .visibility("PUBLIC")
                .previousContent("old")
                .build();

        assertEquals("POST_CREATED", msg.getEventType());
        assertEquals(1, msg.getPostId());
        assertEquals(2, msg.getAuthorId());
        assertEquals("content", msg.getContent());
        assertEquals("PUBLIC", msg.getVisibility());
        assertEquals("old", msg.getPreviousContent());
        assertNotNull(msg.toString());

        IndexPostEventMessage empty = new IndexPostEventMessage();
        empty.setEventType("DELETED");
        assertEquals("DELETED", empty.getEventType());
    }

    @Test
    void testPostSearchResultDTO() {
        PostSearchResultDTO dto = PostSearchResultDTO.builder()
                .postId(1).authorId(2).authorUsername("user")
                .content("hi").mediaUrls(List.of("url"))
                .postType("TEXT").visibility("PUBLIC")
                .likesCount(10).commentsCount(5).sharesCount(2)
                .hashtags(List.of("#java"))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        assertEquals(1, dto.getPostId());
        assertEquals("user", dto.getAuthorUsername());
        assertEquals(10, dto.getLikesCount());
        assertNotNull(new PostSearchResultDTO());
    }

    @Test
    void testUserSearchResultDTO() {
        UserSearchResultDTO dto = UserSearchResultDTO.builder()
                .userId(1).username("user").fullName("Full Name")
                .bio("bio").profilePicUrl("pic").role("USER")
                .build();
        assertEquals(1, dto.getUserId());
        assertEquals("user", dto.getUsername());
        assertNotNull(new UserSearchResultDTO());
    }

    @Test
    void testHashtagResponseDTO() {
        HashtagResponseDTO dto = HashtagResponseDTO.builder()
                .hashtagId(1).tag("java").postCount(100)
                .lastUsedAt(LocalDateTime.now())
                .build();
        assertEquals("java", dto.getTag());
        assertEquals(100, dto.getPostCount());
        assertNotNull(new HashtagResponseDTO());
    }

    @Test
    void testApiResponses() {
        ApiResponseDTO<String> res = ApiResponseDTO.success("msg", "data");
        assertTrue(res.isSuccess());
        assertEquals("data", res.getData());

        ApiResponseDTO<String> err = ApiResponseDTO.error("fail");
        assertFalse(err.isSuccess());

        AuthUserListApiResponse authRes = new AuthUserListApiResponse();
        authRes.setSuccess(true);
        authRes.setData(Collections.emptyList());
        assertTrue(authRes.isSuccess());

        PostApiResponse postRes = new PostApiResponse();
        postRes.setSuccess(true);
        postRes.setData(new PostDataDTO());
        assertNotNull(postRes.getData());

        PostListApiResponse postListRes = new PostListApiResponse();
        postListRes.setData(Collections.emptyList());
        assertNotNull(postListRes.getData());
    }

    @Test
    void testUserDataDTO() {
        UserDataDTO dto = new UserDataDTO();
        dto.setUserId(1);
        dto.setIsActive(true);
        assertEquals(1, dto.getUserId());
        assertTrue(dto.getIsActive());
    }

    @Test
    void testLombokMethods() {
        Hashtag h1 = Hashtag.builder().hashtagId(1).tag("java").build();
        Hashtag h2 = Hashtag.builder().hashtagId(1).tag("java").build();

        assertEquals(h1.getHashtagId(), h2.getHashtagId());
        assertEquals(h1.getTag(), h2.getTag());
        assertNotNull(h1.toString());

        PostHashtag ph1 = PostHashtag.builder().id(1).postId(10).build();
        PostHashtag ph2 = PostHashtag.builder().id(1).postId(10).build();
        assertEquals(ph1.getId(), ph2.getId());
        assertEquals(ph1.getPostId(), ph2.getPostId());
        assertNotNull(ph1.toString());

        IndexPostEventMessage msg1 = IndexPostEventMessage.builder().postId(1).build();
        assertEquals(1, msg1.getPostId());
        assertNotNull(msg1.toString());
    }
}
