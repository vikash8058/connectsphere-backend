package com.connectsphere.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQConfig - RabbitMQ infrastructure setup for notification-service
 *
 * Architecture:
 *   Direct Exchange: connectsphere.notification.exchange
 *   Queues:
 *     1. connectsphere.notification.queue      → single notification events
 *     2. connectsphere.notification.bulk.queue → admin bulk broadcasts
 *
 * Other services (like-service, comment-service, follow-service) publish to the exchange.
 * NotificationListener in this service consumes from the queues.
 *
 * JSON serialization: Jackson2JsonMessageConverter (not Java serialization)
 */
@Configuration
public class RabbitMQConfig {

    @Value("${notification.rabbitmq.exchange}")
    private String exchange;

    @Value("${notification.rabbitmq.queue}")
    private String queue;

    @Value("${notification.rabbitmq.routing-key}")
    private String routingKey;

    @Value("${notification.rabbitmq.bulk-queue}")
    private String bulkQueue;

    @Value("${notification.rabbitmq.bulk-routing-key}")
    private String bulkRoutingKey;

    // ── Exchange ──

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(exchange, true, false);
    }

    // ── Single notification queue ──

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with(routingKey);
    }

    // ── Bulk notification queue ──

    @Bean
    public Queue bulkNotificationQueue() {
        return QueueBuilder.durable(bulkQueue).build();
    }

    @Bean
    public Binding bulkNotificationBinding() {
        return BindingBuilder
                .bind(bulkNotificationQueue())
                .to(notificationExchange())
                .with(bulkRoutingKey);
    }

    // ── JSON message converter (replaces default Java serialization) ──

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}