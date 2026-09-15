package cl.barriodigital.barriodigitalbff.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import cl.barriodigital.barriodigitalbff.security.JwtIdentityExtractor;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

class DownstreamProxyServiceTest {

    private HttpServer server;
    private Headers capturedHeaders;
    private String baseUrl;
    private DownstreamProxyService proxyService;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        proxyService = new DownstreamProxyService(RestClient.create(), new JwtIdentityExtractor());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void authenticatedUserClaimsArePropagatedAsInternalHeaders() {
        authenticate(jwtBuilder()
                .claim("preferred_username", "user@test.cl")
                .claim("oid", "abc-123")
                .claim("roles", List.of("Cliente"))
                .build());

        ResponseEntity<byte[]> response = proxyService.forward(
                baseUrl,
                "trámites",
                request("GET", "/api/requests"),
                null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstHeader("X-User-Email")).isEqualTo("user@test.cl");
        assertThat(firstHeader("X-User-Id")).isEqualTo("abc-123");
        assertThat(firstHeader("X-User-Roles")).isEqualTo("Cliente");
    }

    @Test
    void externalUserHeadersAreIgnoredAndReplacedWithJwtValues() {
        authenticate(jwtBuilder()
                .claim("preferred_username", "real@test.cl")
                .claim("oid", "real-id")
                .claim("roles", List.of("Admin"))
                .build());
        MockHttpServletRequest request = request("POST", "/api/requests");
        request.addHeader("X-User-Email", "hacker@test.cl");
        request.addHeader("x-user-id", "fake-id");
        request.addHeader("X-User-Roles", "Admin,Cliente");
        request.addHeader("Authorization", "Bearer forged-token");

        proxyService.forward(
                baseUrl,
                "trámites",
                request,
                "{}".getBytes(StandardCharsets.UTF_8));

        assertThat(firstHeader("X-User-Email")).isEqualTo("real@test.cl");
        assertThat(firstHeader("X-User-Id")).isEqualTo("real-id");
        assertThat(firstHeader("X-User-Roles")).isEqualTo("Admin");
        assertThat(firstHeader("Authorization")).isNull();
    }

    @Test
    void emailClaimIsUsedWhenPreferredUsernameIsMissing() {
        authenticate(jwtBuilder()
                .claim("email", "mail-claim@test.cl")
                .claim("oid", "abc-123")
                .claim("roles", List.of("Cliente"))
                .build());

        proxyService.forward(
                baseUrl,
                "trámites",
                request("GET", "/api/requests"),
                null);

        assertThat(firstHeader("X-User-Email")).isEqualTo("mail-claim@test.cl");
    }

    @Test
    void subjectIsUsedAsUserIdWhenOidIsMissing() {
        authenticate(jwtBuilder()
                .subject("subject-456")
                .claim("preferred_username", "user@test.cl")
                .claim("roles", List.of("Cliente"))
                .build());

        proxyService.forward(
                baseUrl,
                "trámites",
                request("GET", "/api/requests"),
                null);

        assertThat(firstHeader("X-User-Id")).isEqualTo("subject-456");
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        capturedHeaders = exchange.getRequestHeaders();
        exchange.getRequestBody().readAllBytes();
        byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
        }
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContentType("application/json");
        return request;
    }

    private void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("SCOPE_access_as_user")),
                        jwt.getSubject()));
    }

    private Jwt.Builder jwtBuilder() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("validated-token")
                .header("alg", "none")
                .subject("user-subject")
                .issuer("https://login.microsoftonline.com/test-tenant/v2.0")
                .audience(List.of("api://test-api"))
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(3600));
    }

    private String firstHeader(String name) {
        return capturedHeaders.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .findFirst()
                .flatMap(entry -> entry.getValue().stream().findFirst())
                .orElse(null);
    }
}
