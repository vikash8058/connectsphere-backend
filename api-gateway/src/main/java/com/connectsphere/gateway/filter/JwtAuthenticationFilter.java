package com.connectsphere.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT Authentication Filter
 *
 * Applied on all protected routes defined in application.yml.
 * Validates the Bearer token from the Authorization header.
 * On success, forwards userId and role as headers to downstream services.
 * On failure, returns 401 Unauthorized immediately.
 *
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;   // Same secret as auth-service

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            String path = exchange.getRequest().getURI().getPath();
            log.debug("JWT Filter triggered for path: {}", path);

            // Extract Authorization header
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            // Missing or malformed header → 401
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header for path: {}", path);
                return onUnauthorized(exchange, "Missing or invalid Authorization header");
            }

            // Strip "Bearer " prefix
            String token = authHeader.substring(7);

            try {
                // Validate and parse JWT using same secret as auth-service
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.get("userId", Integer.class).toString();
                String role   = claims.get("role", String.class);
                String email  = claims.getSubject();

                log.debug("JWT valid — userId: {}, role: {}, email: {}", userId, role, email);

                // Forward user info as custom headers to downstream services
                ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(r -> r
                            .header("X-User-Id", userId)     // Downstream can read who made the request
                            .header("X-User-Role", role)     // Downstream can enforce role-based access
                            .header("X-User-Email", email)   // Downstream uses email for principal
                        )
                        .build();

                return chain.filter(mutatedExchange);

            } catch (Exception e) {
                log.error("JWT validation failed: {}", e.getMessage());
                return onUnauthorized(exchange, "Invalid or expired token");
            }
        };
    }

    // Returns 401 Unauthorized response
    private Mono<Void> onUnauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        String body = "{\"error\": \"" + message + "\", \"status\": 401}";
        var buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    // Config class (no fields needed — filter is stateless)
    public static class Config {
    }
}