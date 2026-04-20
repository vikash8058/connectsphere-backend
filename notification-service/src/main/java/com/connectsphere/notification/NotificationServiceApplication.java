package com.connectsphere.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * NotificationServiceApplication - Entry point for ConnectSphere Notification Service
 *
 * Responsibilities (case study section 4.6):
 *   - Create and store in-app notifications for social events
 *     (LIKE, COMMENT, REPLY, FOLLOW, MENTION)
 *   - Track read/unread state per notification
 *   - Expose unread badge count for top navigation bar
 *   - Asynchronous dispatch via RabbitMQ (fire-and-forget from other services)
 *   - Bulk notification dispatch for admin broadcasts
 *   - Email alerts for high-priority events
 *
 * Port: 8086
 * DB:   connectsphere_notification
 */
@SpringBootApplication
@EnableAsync
@EnableFeignClients
public class NotificationServiceApplication {

    public static void main(String[] args) {

        SpringApplication.run(NotificationServiceApplication.class, args);
        System.out.println("ConnectSphere Notification Service is running on port 8086...");
    }
}