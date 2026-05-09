package com.connectsphere.like.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostApiResponseTest {

    @Test
    void testGettersAndSetters() {
        PostApiResponse response = new PostApiResponse();
        response.setSuccess(true);
        PostApiResponse.PostData data = new PostApiResponse.PostData();
        data.setPostId(101);
        response.setData(data);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(101, response.getData().getPostId());
    }
}
