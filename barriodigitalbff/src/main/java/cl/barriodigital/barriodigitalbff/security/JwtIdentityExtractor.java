package cl.barriodigital.barriodigitalbff.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtIdentityExtractor {

    public AuthenticatedUserHeaders currentUserHeaders() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return fromJwt(jwtAuthenticationToken.getToken());
        }

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return fromJwt(jwt);
        }

        return new AuthenticatedUserHeaders(null, null, List.of());
    }

    public AuthenticatedUserHeaders fromJwt(Jwt jwt) {
        String email = firstNonBlank(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"));
        String userId = firstNonBlank(
                jwt.getClaimAsString("oid"),
                jwt.getSubject());

        return new AuthenticatedUserHeaders(email, userId, getRoles(jwt));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return null;
    }

    private List<String> getRoles(Jwt jwt) {
        Object rolesClaim = jwt.getClaim("roles");

        if (rolesClaim instanceof Collection<?> roles) {
            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(StringUtils::hasText)
                    .toList();
        }

        if (rolesClaim instanceof String role && StringUtils.hasText(role)) {
            return List.of(role);
        }

        return List.of();
    }
}
