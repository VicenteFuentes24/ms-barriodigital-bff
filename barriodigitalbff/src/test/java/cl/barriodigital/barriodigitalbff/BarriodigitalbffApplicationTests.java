package cl.barriodigital.barriodigitalbff;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/test-tenant/v2.0",
        "barriodigital.security.audience=api://test-api",
        "barriodigital.frontend.origin=http://localhost:5173",
        "barriodigital.services.requests-url=http://127.0.0.1:1",
        "barriodigital.services.catalog-url=http://127.0.0.1:1",
        "barriodigital.services.report-url=http://127.0.0.1:1",
        "barriodigital.services.audit-url=http://127.0.0.1:1"
})
@AutoConfigureMockMvc
class BarriodigitalbffApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpointWithoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Se requiere un token de acceso válido."));
    }

    @Test
    void meWithJwtAndScopeReturnsSafeClaims() throws Exception {
        mockMvc.perform(get("/api/bff/me").with(jwtWithRole("Admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("user-123"))
                .andExpect(jsonPath("$.username").value("test@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("Admin"))
                .andExpect(jsonPath("$.scopes[0]").value("access_as_user"));
    }

    @Test
    void clienteCannotUpdateRequestStatus() throws Exception {
        mockMvc.perform(put("/api/requests/REQ-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ADMITIDO\"}")
                        .with(jwtWithRole("Cliente")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void operadorCanReachRequestStatusProxy() throws Exception {
        mockMvc.perform(put("/api/requests/REQ-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ADMITIDO\"}")
                        .with(jwtWithRole("Operador")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void operadorCannotPostCatalog() throws Exception {
        mockMvc.perform(post("/api/catalog/procedures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"requirements\":[],\"dailyQuota\":1}")
                        .with(jwtWithRole("Operador")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReachCatalogPostProxy() throws Exception {
        mockMvc.perform(post("/api/catalog/procedures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"requirements\":[],\"dailyQuota\":1}")
                        .with(jwtWithRole("Admin")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void auditorCanReachAuditProxy() throws Exception {
        mockMvc.perform(get("/api/audit").with(jwtWithRole("Auditor")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void clienteCannotGetAudit() throws Exception {
        mockMvc.perform(get("/api/audit").with(jwtWithRole("Cliente")))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor jwtWithRole(String role) {
        return jwt()
                .jwt(jwt -> jwt
                        .subject("user-123")
                        .issuer("https://login.microsoftonline.com/test-tenant/v2.0")
                        .audience(List.of("api://test-api"))
                        .issuedAt(Instant.now().minusSeconds(60))
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .claim("name", "Test User")
                        .claim("preferred_username", "test@example.com")
                        .claim("roles", List.of(role))
                        .claim("scp", "access_as_user"))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_access_as_user"),
                        new SimpleGrantedAuthority("ROLE_" + role));
    }
}

