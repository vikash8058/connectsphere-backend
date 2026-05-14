package com.connectsphere.media.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class FileSystemStorageService implements StorageService {

    @Value("${media.storage-base-path}")
    private String storageBasePath;

    @Value("${media.cdn-base-url}")
    private String cdnBaseUrl;

    @Override
    public String store(InputStream inputStream, String fileName, String contentType) {
        try {
            Path uploadPath = Paths.get(storageBasePath);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            Files.copy(inputStream, uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            log.info("File saved to local storage: {}/{}", storageBasePath, fileName);
            
            return String.format("%s/%s", cdnBaseUrl, fileName);
        } catch (IOException e) {
            log.error("Failed to save file locally: {}", e.getMessage());
            throw new RuntimeException("Could not store file locally", e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        try {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            Path filePath = Paths.get(storageBasePath).resolve(fileName);
            Files.deleteIfExists(filePath);
            log.info("File deleted from local storage: {}", fileName);
        } catch (IOException e) {
            log.warn("Failed to delete file locally: {}", e.getMessage());
        }
    }
}
