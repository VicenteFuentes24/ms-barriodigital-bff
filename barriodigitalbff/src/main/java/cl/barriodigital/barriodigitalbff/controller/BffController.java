package cl.barriodigital.barriodigitalbff.controller;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bff")
public class BffController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "ms-barriodigital-bff");
    }

    @GetMapping("/me")
    public BffMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new BffMeResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("name"),
                getUsername(jwt),
                getRoles(jwt),
                getScopes(jwt),
                jwt.getIssuer() != null ? jwt.getIssuer().toString() : null,
                jwt.getAudience(),
                jwt.getExpiresAt());
    }

    private String getUsername(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }

        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            return email;
        }

        return jwt.getSubject();
    }

    private List<String> getRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null ? roles : List.of();
    }

    private List<String> getScopes(Jwt jwt) {
        String scopes = jwt.getClaimAsString("scp");
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }

        return Arrays.stream(scopes.split(" "))
                .filter(scope -> !scope.isBlank())
                .toList();
    }

    public record BffMeResponse(
            String subject,
            String name,
            String username,
            List<String> roles,
            List<String> scopes,
            String issuer,
            List<String> audience,
            Instant expiresAt) {
    }
}
