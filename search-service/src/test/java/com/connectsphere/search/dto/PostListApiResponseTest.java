package com.connectsphere.search.dto;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class PostListApiResponseTest {

    @Test
    void testGettersAndSetters() {
        PostListApiResponse response = new PostListApiResponse();
        response.setSuccess(true);
        response.setMessage("Found");
        response.setData(Collections.emptyList());

        assertTrue(response.isSuccess());
        assertEquals("Found", response.getMessage());
        assertNotNull(response.getData());
    }
}
