package cl.barriodigital.barriodigitalbff.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    @Test
    void validAudienceReturnsSuccess() {
        AudienceValidator validator = new AudienceValidator("api://expected-api");

        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience("api://expected-api"));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void invalidAudienceReturnsFailure() {
        AudienceValidator validator = new AudienceValidator("api://expected-api");

        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience("api://other-api"));

        assertThat(result.hasErrors()).isTrue();
    }

    private Jwt jwtWithAudience(String audience) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .audience(List.of(audience))
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
