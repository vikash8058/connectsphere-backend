package com.connectsphere.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SearchServiceApplication - Entry point for ConnectSphere Search/Hashtag Service
 *
 * Responsibilities (case study section 4.8):
 *   - Index post content to extract and persist #hashtags
 *   - Compute trending hashtags by post-count ranking
 *   - Full-text search across posts (by keyword)
 *   - Full-text search across users (by username/fullName)
 *   - Provide PostHashtag mapping (many-to-many: posts ↔ hashtags)
 *
 * Inter-service:
 *   - Listens to RabbitMQ post.created / post.updated / post.deleted events
 *     from post-service to keep hashtag index in sync
 *   - Calls post-service via Feign to enrich search results with post details
 *   - Calls auth-service via Feign to enrich user search results
 *
 * Port: 8087
 * DB  : connectsphere_search
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
public class SearchServiceApplication {

    public static void main(String[] args) {

        SpringApplication.run(SearchServiceApplication.class, args);
        System.out.println("ConnectSphere Search Service is running on port 8088...");
    }
}
