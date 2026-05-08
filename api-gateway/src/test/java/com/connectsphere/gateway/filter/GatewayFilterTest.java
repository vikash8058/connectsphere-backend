package com.connectsphere.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("API Gateway Filter Tests")
class GatewayFilterTest {

    // ── Shared Constants ───

    // Must be ≥ 256 bits (32 chars) for HS256
    private static final String TEST_SECRET = "connectsphere_test_jwt_secret_key_min32!";
    private static final Integer TEST_USER_ID = 101;
    private static final String  TEST_ROLE     = "USER";
    private static final String  TEST_EMAIL    = "vikash@test.com";

    // ── JWT Token Builder ───

    /**
     * Generates a valid signed JWT matching the filter's parsing logic.
     * Claims: userId (Integer), role (String), subject = email.
     */
    private String buildValidToken(Integer userId, String role, String email, long expiryMs) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(key)
                .compact();
    }

    private String buildExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(TEST_EMAIL)
                .claim("userId", TEST_USER_ID)
                .claim("role", TEST_ROLE)
                .issuedAt(new Date(System.currentTimeMillis() - 10000))
                .expiration(new Date(System.currentTimeMillis() - 5000)) // expired 5s ago
                .signWith(key)
                .compact();
    }

    // ── JwtAuthenticationFilter Tests ──

    @Nested
    @DisplayName("JwtAuthenticationFilter")
    class JwtAuthenticationFilterTests {

        private JwtAuthenticationFilter filter;
        private GatewayFilter gatewayFilter;

        @BeforeEach
        void setUp() {
            filter = new JwtAuthenticationFilter();
            ReflectionTestUtils.setField(filter, "jwtSecret", TEST_SECRET);
            gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());
        }

        @Test
        @DisplayName("Should return 401 when Authorization header is missing")
        void filter_missingAuthHeader_returns401() {
            // Given — request with no Authorization header
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/payments/subscription-status")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            GatewayFilterChain chain = mock(GatewayFilterChain.class);

            // When
            gatewayFilter.filter(exchange, chain).block();

            // Then
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("Should return 401 when Authorization header doesn't start with Bearer")
        void filter_invalidAuthHeaderFormat_returns401() {
            // Given — Basic auth instead of Bearer
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/payments/create-order")
                    .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            GatewayFilterChain chain = mock(GatewayFilterChain.class);

            // When
            gatewayFilter.filter(exchange, chain).block();

            // Then
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("Should return 401 for expired JWT token")
        void filter_expiredToken_returns401() {
            // Given
            String expiredToken = buildExpiredToken();
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/payments/verify")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            GatewayFilterChain chain = mock(GatewayFilterChain.class);

            // When
            gatewayFilter.filter(exchange, chain).block();

            // Then
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("Should return 401 for tampered/invalid JWT token")
        void filter_invalidToken_returns401() {
            // Given — valid structure but wrong signature
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/search/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer this.is.not.a.valid.jwt")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            GatewayFilterChain chain = mock(GatewayFilterChain.class);

            // When
            gatewayFilter.filter(exchange, chain).block();

            // Then
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("Should forward request with X-User-Id, X-User-Role, X-User-Email headers on valid token")
        void filter_validToken_forwardsHeadersToDownstream() {
            // Given
            String validToken = buildValidToken(TEST_USER_ID, TEST_ROLE, TEST_EMAIL, 3_600_000);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/payments/subscription-status")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            GatewayFilterChain chain = mock(GatewayFilterChain.class);
            // Capture the mutated exchange passed to chain.filter()
            ArgumentCaptor<org.springframework.web.server.ServerWebExchange> exchangeCaptor =
                    ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
            when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

            // When
            gatewayFilter.filter(exchange, chain).block();

            // Then — chain.filter() was called (request allowed through)
            verify(chain).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);

            // Downstream exchange has the injected headers
            org.springframework.web.server.ServerWebExchange mutated = exchangeCaptor.getValue();
            assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Id"))
                    .isEqualTo(TEST_USER_ID.toString());
            assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Role"))
                    .isEqualTo(TEST_ROLE);
            assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Email"))
                    .isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("Should forward ADMIN role correctly in X-User-Role header")
        void filter_adminToken_forwardsAdminRole() {
            // Given
            String adminToken = buildValidToken(1, "ADMIN", "admin@connectsphere.com", 3_600_000);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/admin/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            GatewayFilterChain chain = mock(GatewayFilterChain.class);
            ArgumentCaptor<ServerWebExchange> captor =
                    ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
            when(chain.filter(captor.capture())).thenReturn(Mono.empty());

            // When
            gatewayFilter.filter(exchange, chain).block();

            // Then
            verify(chain).filter(any());
            assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Role"))
                    .isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("Should return 401 for empty Bearer token (Bearer with no value)")
        void filter_emptyBearerToken_returns401() {
            // Given — "Bearer " with nothing after
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/payments/create-order")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            GatewayFilterChain chain = mock(GatewayFilterChain.class);

            // When
            gatewayFilter.filter(exchange, chain).block();

            // Then
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("Should return 401 for token signed with different secret")
        void filter_tokenSignedWithWrongSecret_returns401() {
            // Given — token signed with a DIFFERENT secret
            String wrongSecret = "completely_different_secret_key_!!32chars";
            SecretKey wrongKey = Keys.hmacShaKeyFor(wrongSecret.getBytes(StandardCharsets.UTF_8));
            String tokenWithWrongSecret = Jwts.builder()
                    .subject(TEST_EMAIL)
                    .claim("userId", TEST_USER_ID)
                    .claim("role", TEST_ROLE)
                    .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                    .signWith(wrongKey)
                    .compact();

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/payments/verify")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithWrongSecret)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            GatewayFilterChain chain = mock(GatewayFilterChain.class);

            // When
            gatewayFilter.filter(exchange, chain).block();

            // Then
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }
    }

    // ── LoggingFilter Tests ────

    @Nested
    @DisplayName("LoggingFilter")
    class LoggingFilterTests {

        private LoggingFilter loggingFilter;

        @BeforeEach
        void setUp() {
            loggingFilter = new LoggingFilter();
        }

        @Test
        @DisplayName("Should have order -1 (runs before JWT filter)")
        void loggingFilter_order_isMinusOne() {
            assertThat(loggingFilter.getOrder()).isEqualTo(-1);
        }

        @Test
        @DisplayName("Should pass request through to chain (does not block)")
        void loggingFilter_passesRequestThrough() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/search/trending")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            GatewayFilterChain chain = mock(GatewayFilterChain.class);
            when(chain.filter(exchange)).thenReturn(Mono.empty());

            // When
            Mono<Void> result = loggingFilter.filter(exchange, chain);

            // Then — chain.filter() is called, request is not blocked
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("Should pass POST request through to chain")
        void loggingFilter_postRequest_passesThrough() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/payments/create-order")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            GatewayFilterChain chain = mock(GatewayFilterChain.class);
            when(chain.filter(exchange)).thenReturn(Mono.empty());

            // When + Then
            StepVerifier.create(loggingFilter.filter(exchange, chain))
                    .verifyComplete();
            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("Should not modify the exchange (read-only logging)")
        void loggingFilter_doesNotMutateExchange() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/notifications/unread")
                    .header("X-User-Id", "42")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            GatewayFilterChain chain = mock(GatewayFilterChain.class);
            when(chain.filter(exchange)).thenReturn(Mono.empty());

            // When
            loggingFilter.filter(exchange, chain).block();

            // Then — original header still intact, not modified
            assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
            // Exchange passed to chain is the same object (not mutated)
            verify(chain).filter(exchange);
        }
    }
}