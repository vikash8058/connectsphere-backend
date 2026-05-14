package com.connectsphere.media.service;

import java.io.InputStream;

/**
 * StorageService - Abstraction for media storage.
 * Can be implemented for Local File System, OCI Object Storage, or Cloudinary.
 */
public interface StorageService {
    
    /**
     * Upload a file and return the full URL.
     */
    String store(InputStream inputStream, String fileName, String contentType);
    
    /**
     * Delete a file from storage.
     */
    void delete(String fileUrl);
}
