package com.connectsphere.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;

/**
 * Global Logging Filter
 *
 * Runs for EVERY request passing through the gateway.
 * Logs method, path, and response status automatically.
 * No configuration needed — applied globally.
 */
@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String method = exchange.getRequest().getMethod().name();
        String path   = exchange.getRequest().getURI().getPath();

        log.info(">>> Incoming Request: {} {}", method, path);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
            log.info("<<< Response: {} {} → {}", method, path, status);
        }));
    }

    @Override
    public int getOrder() {
        // Run before JWT filter (lower number = higher priority)
        return -1;
    }
}