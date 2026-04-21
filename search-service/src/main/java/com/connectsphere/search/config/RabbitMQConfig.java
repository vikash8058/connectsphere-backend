package com.connectsphere.search.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQConfig - RabbitMQ configuration for Search Service
 *
 * search-service CONSUMES from three queues:
 *   connectsphere.search.post.created.queue  ← post-service publishes POST_CREATED
 *   connectsphere.search.post.updated.queue  ← post-service publishes POST_UPDATED
 *   connectsphere.search.post.deleted.queue  ← post-service publishes POST_DELETED
 *
 * All three queues bind to the same exchange (connectsphere.post.exchange)
 * with different routing keys.
 *
 * NOTE: post-service must also declare the same exchange and publish to it.
 * Exchanges and queues are idempotently declared on startup — safe to declare
 * from both sides.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${search.rabbitmq.post-exchange}")
    private String postExchange;

    @Value("${search.rabbitmq.post-created-queue}")
    private String postCreatedQueue;

    @Value("${search.rabbitmq.post-created-routing-key}")
    private String postCreatedRoutingKey;

    @Value("${search.rabbitmq.post-updated-queue}")
    private String postUpdatedQueue;

    @Value("${search.rabbitmq.post-updated-routing-key}")
    private String postUpdatedRoutingKey;

    @Value("${search.rabbitmq.post-deleted-queue}")
    private String postDeletedQueue;

    @Value("${search.rabbitmq.post-deleted-routing-key}")
    private String postDeletedRoutingKey;

    // ── Exchange ──────────────────────────────────────────────────────────────

    @Bean
    public DirectExchange postExchange() {
        return new DirectExchange(postExchange, true, false);
    }

    // ── Queues ────────────────────────────────────────────────────────────────

    @Bean
    public Queue postCreatedQueue() {
        return QueueBuilder.durable(postCreatedQueue).build();
    }

    @Bean
    public Queue postUpdatedQueue() {
        return QueueBuilder.durable(postUpdatedQueue).build();
    }

    @Bean
    public Queue postDeletedQueue() {
        return QueueBuilder.durable(postDeletedQueue).build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────

    @Bean
    public Binding postCreatedBinding() {
        return BindingBuilder
                .bind(postCreatedQueue())
                .to(postExchange())
                .with(postCreatedRoutingKey);
    }

    @Bean
    public Binding postUpdatedBinding() {
        return BindingBuilder
                .bind(postUpdatedQueue())
                .to(postExchange())
                .with(postUpdatedRoutingKey);
    }

    @Bean
    public Binding postDeletedBinding() {
        return BindingBuilder
                .bind(postDeletedQueue())
                .to(postExchange())
                .with(postDeletedRoutingKey);
    }

    // ── JSON Converter (same as all other services) ───────────────────────────

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
