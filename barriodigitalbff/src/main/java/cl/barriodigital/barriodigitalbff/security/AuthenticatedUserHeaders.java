package cl.barriodigital.barriodigitalbff.security;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

public record AuthenticatedUserHeaders(
        String email,
        String userId,
        List<String> roles
) {

    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    public void applyTo(HttpHeaders headers) {
        if (StringUtils.hasText(email)) {
            headers.set(USER_EMAIL_HEADER, email);
        }

        if (StringUtils.hasText(userId)) {
            headers.set(USER_ID_HEADER, userId);
        }

        if (roles != null && !roles.isEmpty()) {
            headers.set(USER_ROLES_HEADER, String.join(",", roles));
        }
    }
}
