package com.connectsphere.search.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseDTOTest {

    @Test
    void testStaticMethods() {
        ApiResponseDTO<String> successWithData = ApiResponseDTO.success("Ok", "Payload");
        assertTrue(successWithData.isSuccess());
        assertEquals("Ok", successWithData.getMessage());
        assertEquals("Payload", successWithData.getData());

        ApiResponseDTO<Object> successOnly = ApiResponseDTO.success("Done");
        assertTrue(successOnly.isSuccess());
        assertEquals("Done", successOnly.getMessage());

        ApiResponseDTO<Object> error = ApiResponseDTO.error("Fail");
        assertFalse(error.isSuccess());
        assertEquals("Fail", error.getMessage());
    }

    @Test
    void testGettersAndSetters() {
        ApiResponseDTO<Integer> dto = new ApiResponseDTO<>();
        dto.setSuccess(true);
        dto.setMessage("Test");
        dto.setData(100);
        
        assertTrue(dto.isSuccess());
        assertEquals("Test", dto.getMessage());
        assertEquals(100, dto.getData());
        assertNotNull(dto.getTimestamp());
    }
}
