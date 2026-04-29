package com.connectsphere.post.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {

    @Value("${post.rabbitmq.exchange:connectsphere.post.exchange}")
    private String postExchange;

    /**
     * Declare the Direct exchange that post-service publishes to.
     * search-service will bind its queues to this exchange.
     */
    @Bean
    public DirectExchange postExchange() {
        return new DirectExchange(postExchange, true, false);
    }

    /**
     * Jackson2 message converter for JSON serialization/deserialization.
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate for publishing messages.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}

