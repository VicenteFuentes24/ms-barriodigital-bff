package cl.barriodigital.barriodigitalbff;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real BFF security/decoder/proxy; only the JWK endpoint and downstream HTTP server are isolated test fixtures. */
@SpringBootTest(properties={"spring.config.import=", "debug=false", "logging.level.root=WARN",
        "logging.level.org.springframework=WARN", "barriodigital.frontend.origin=http://localhost:5173",
        "barriodigital.http.connect-timeout-ms=250", "barriodigital.http.read-timeout-ms=250"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class ReportProxyIntegrationTest {
    private static final String AUDIENCE="api://bff-report-test";
    private static final RSAKey KEY=key();
    private static final ExecutorService EXECUTOR=Executors.newCachedThreadPool();
    private static final BlockingQueue<Captured> CAPTURED=new LinkedBlockingQueue<>();
    private static final AtomicReference<Reply> REPLY=new AtomicReference<>();
    private static final HttpServer SERVER=server();
    private static final String BASE="http://127.0.0.1:"+SERVER.getAddress().getPort();
    private static final String ISSUER=BASE+"/tenant/v2.0";
    @Autowired MockMvc mvc;

    @DynamicPropertySource static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",() -> ISSUER);
        registry.add("barriodigital.security.audience",() -> AUDIENCE);
        registry.add("barriodigital.services.report-url",() -> BASE+"/");
        for (String service : List.of("requests","catalog","audit")) registry.add("barriodigital.services."+service+"-url",() -> BASE);
    }
    @BeforeEach void reset() {
        CAPTURED.clear(); REPLY.set(new Reply(200,"application/json","{\"ok\":true}",0,0));
    }
    @AfterAll static void stop() { SERVER.stop(0); EXECUTOR.shutdownNow(); }

    @ParameterizedTest
    @CsvSource({"kpis,last24h,kpis.json","top-procedures,last7d,top-procedures.json","requests-by-hour,last24h,requests-by-hour.json"})
    void reportRoutesKeepQueryStatusContentTypeAndExactBody(String path,String range,String fixture) throws Exception {
        String json=fixture(fixture); REPLY.set(new Reply(200,"application/json",json,0,0));
        mvc.perform(authorized(get(URI.create("/api/report/"+path+"?range="+range)),"Admin","access_as_user"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/json"))
                .andExpect(content().bytes(json.getBytes(StandardCharsets.UTF_8)));
        var captured=take(); assertThat(captured.uri()).isEqualTo("/api/report/"+path+"?range="+range);
        assertThat(captured.authorizationPresent()).isFalse();
        assertThat(captured.roles()).isEqualTo("Admin");
    }
    @Test void encodedDatesLimitRepeatedAndEmptyQueryParametersSurvive() throws Exception {
        String query="from=2026-11-01T00%3A00%3A00%2B00%3A00&to=2026-11-02T00%3A00%3A00Z&limit=5&tag=a%26b&tag=c%2Bd&empty=";
        mvc.perform(authorized(get(URI.create("/api/report/top-procedures?"+query)),"Admin","access_as_user"))
                .andExpect(status().isOk());
        assertThat(take().uri()).isEqualTo("/api/report/top-procedures?"+query);
    }
    @Test void absentQueryAndTrailingSlashInServiceUrlDoNotDuplicatePrefix() throws Exception {
        mvc.perform(authorized(get("/api/report/kpis"),"Admin","access_as_user")).andExpect(status().isOk());
        assertThat(take().uri()).isEqualTo("/api/report/kpis");
    }
    @ParameterizedTest @ValueSource(ints={400,404,500,503})
    void downstreamProblemDetailsArePreservedWithoutAddingInternalErrors(int code) throws Exception {
        String body="{\"type\":\"about:blank\",\"title\":\"Report error\",\"status\":"+code+",\"detail\":\"Safe public error\",\"instance\":\"/api/report/kpis\"}";
        REPLY.set(new Reply(code,"application/problem+json",body,0,0));
        mvc.perform(authorized(get("/api/report/kpis"),"Admin","access_as_user"))
                .andExpect(status().is(code)).andExpect(content().contentType("application/problem+json"))
                .andExpect(content().bytes(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings={"/api/report","/api/report/kpis","/api/report/top-procedures","/api/report/requests-by-hour"})
    void withoutTokenReturns401WithoutForwarding(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized()); assertThat(CAPTURED).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"Operador","Cliente","Auditor","admin",""})
    void otherRolesWithScopeCannotReadReport(String role) throws Exception {
        for (String path : List.of("/api/report","/api/report/kpis","/api/report/top-procedures","/api/report/requests-by-hour")) {
            mvc.perform(authorized(get(path),role,"access_as_user")).andExpect(status().isForbidden());
        }
        assertThat(CAPTURED).isEmpty();
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"different_scope"})
    void adminWithoutRequiredScopeIsForbidden(String scope) throws Exception {
        mvc.perform(authorized(get("/api/report/kpis"),"Admin",scope)).andExpect(status().isForbidden()); assertThat(CAPTURED).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"malformed","signature","issuer","audience","expired"})
    void invalidJwtIsRejectedByRealDecoder(String reason) throws Exception {
        String token=reason.equals("malformed") ? "not-a-jwt" : token("Admin","access_as_user",
                reason.equals("signature") ? key() : KEY, reason.equals("issuer") ? BASE+"/wrong-issuer" : ISSUER,
                reason.equals("audience") ? "api://wrong-audience" : AUDIENCE,
                reason.equals("expired") ? Instant.now().minusSeconds(300) : Instant.now().plusSeconds(300));
        mvc.perform(get("/api/report/kpis").header("Authorization","Bearer "+token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.trace").doesNotExist());
        assertThat(CAPTURED).isEmpty();
    }
    @Test void claimsReplaceSpoofedIdentityAndBearerTokenDoesNotReachReport() throws Exception {
        mvc.perform(authorized(get("/api/report/kpis"),"Admin","access_as_user")
                .header("X-User-Email","forged@example.invalid").header("X-User-Id","forged").header("X-User-Roles","Auditor"))
                .andExpect(status().isOk());
        var captured=take(); assertThat(captured.authorizationPresent()).isFalse();
        assertThat(captured.email()).isEqualTo("admin@example.invalid"); assertThat(captured.id()).isEqualTo("test-oid");
        assertThat(captured.roles()).isEqualTo("Admin");
    }
    @Test void slowHeadersReturnSafe503() throws Exception { assertTimeout(new Reply(200,"application/json","{\"private\":\"never forwarded\"}",1500,0)); }
    @Test void slowBodyReturnsSafe503() throws Exception { assertTimeout(new Reply(200,"application/json","{\"private\":\"never forwarded\"}",0,1500)); }
    private void assertTimeout(Reply reply) throws Exception {
        REPLY.set(reply); long start=System.nanoTime();
        var result=mvc.perform(authorized(get("/api/report/kpis"),"Admin","access_as_user"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.path").value("/api/report/kpis")).andReturn();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)).isLessThan(1300);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("never forwarded","Exception",BASE,"Bearer");
    }
    @Test void unavailableConnectionReturnsSafe503() throws Exception {
        // A local test peer closes the connection without any HTTP response. No real service is stopped.
        REPLY.set(new Reply(0,"application/json","",0,0));
        mvc.perform(authorized(get("/api/report/kpis"),"Admin","access_as_user"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
    @ParameterizedTest @CsvSource({"GET,/api/requests?status=INGRESADO,Cliente", "POST,/api/requests,Cliente",
            "PUT,/api/requests/1/status,Operador", "GET,/api/catalog/procedures,Cliente", "POST,/api/catalog/procedures,Admin",
            "PUT,/api/catalog/procedures/1,Admin", "GET,/api/audit,Auditor"})
    void authorizedExistingProxiesStillForwardMethodPathAndBody(String method,String path,String role) throws Exception {
        String body=method.equals("GET") ? "" : "{\"field\":\"value\"}";
        mvc.perform(authorized(request(org.springframework.http.HttpMethod.valueOf(method),URI.create(path)),role,"access_as_user")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        var captured=take(); assertThat(captured.uri()).isEqualTo(path); assertThat(captured.method()).isEqualTo(method);
        assertThat(captured.body()).isEqualTo(body); assertThat(captured.roles()).isEqualTo(role);
    }
    @ParameterizedTest @CsvSource({"PUT,/api/requests/1/status,Cliente", "GET,/api/requests,Auditor",
            "POST,/api/catalog/procedures,Operador", "GET,/api/audit,Cliente", "GET,/api/audit,Operador", "POST,/api/audit,Admin"})
    void existingRoleRestrictionsRemainEnforced(String method,String path,String role) throws Exception {
        mvc.perform(authorized(request(org.springframework.http.HttpMethod.valueOf(method),URI.create(path)),role,"access_as_user")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        assertThat(CAPTURED).isEmpty();
    }
    @Test void meUsesValidatedClaimsAndRequiredScopeWithoutCallingDownstream() throws Exception {
        mvc.perform(authorized(get("/api/bff/me"),"Cliente","access_as_user"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("Cliente"))
                .andExpect(jsonPath("$.scopes[0]").value("access_as_user")).andExpect(jsonPath("$.subject").value("test-user"));
        mvc.perform(authorized(get("/api/bff/me"),"Admin",null)).andExpect(status().isForbidden());
        assertThat(CAPTURED).isEmpty();
    }
    @Test void frontendPreflightKeepsConfiguredOriginWithoutMakingReportPublic() throws Exception {
        mvc.perform(options("/api/report/kpis").header("Origin","http://localhost:5173")
                .header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
        mvc.perform(get("/api/report/kpis").header("Origin","http://localhost:5173")).andExpect(status().isUnauthorized());
        assertThat(CAPTURED).isEmpty();
    }
    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request,String role,String scope) throws Exception {
        return request.header("Authorization","Bearer "+token(role,scope,KEY,ISSUER,AUDIENCE,Instant.now().plusSeconds(300)));
    }
    private String token(String role,String scope,RSAKey signingKey,String issuer,String audience,Instant expiry) throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("test-user")
                .issueTime(Date.from(Instant.now().minusSeconds(600))).expirationTime(Date.from(expiry))
                .claim("preferred_username","admin@example.invalid").claim("oid","test-oid")
                .claim("roles",role.isEmpty() ? List.of() : List.of(role));
        if (scope!=null) claims.claim("scp",scope);
        var signed=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),claims.build());
        signed.sign(new RSASSASigner(signingKey)); return signed.serialize();
    }
    private static RSAKey key() {
        try { return new RSAKeyGenerator(2048).keyID("ephemeral-bff-test").generate(); }
        catch (Exception e) { throw new IllegalStateException("Unable to generate isolated test key",e); }
    }
    private static HttpServer server() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setExecutor(EXECUTOR); server.createContext("/",ReportProxyIntegrationTest::handle); server.start(); return server;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private static void handle(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getPath().equals("/tenant/discovery/v2.0/keys")) {
            byte[] publicKeys=new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(200,publicKeys.length);
            try (exchange) { exchange.getResponseBody().write(publicKeys); } return;
        }
        var headers=exchange.getRequestHeaders(); var reply=REPLY.get();
        CAPTURED.add(new Captured(exchange.getRequestURI().toASCIIString(),exchange.getRequestMethod(),
                new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),headers.containsKey("Authorization"),
                headers.getFirst("X-User-Email"),headers.getFirst("X-User-Id"),headers.getFirst("X-User-Roles")));
        try (exchange) {
            if (reply.status()==0) return;
            Thread.sleep(reply.beforeHeaders());
            byte[] body=reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",reply.contentType()); exchange.sendResponseHeaders(reply.status(),body.length);
            Thread.sleep(reply.afterHeaders()); exchange.getResponseBody().write(body);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (IOException expectedWhenTimedOut) { /* The client closed its test connection after the read timeout. */ }
    }
    private Captured take() throws InterruptedException {
        var result=CAPTURED.poll(2,TimeUnit.SECONDS); assertThat(result).isNotNull(); return result;
    }
    private String fixture(String name) throws IOException {
        try (var input=getClass().getResourceAsStream("/report/"+name)) {
            assertThat(input).isNotNull(); return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private record Captured(String uri,String method,String body,boolean authorizationPresent,String email,String id,String roles) { }
    private record Reply(int status,String contentType,String body,int beforeHeaders,int afterHeaders) { }
}
