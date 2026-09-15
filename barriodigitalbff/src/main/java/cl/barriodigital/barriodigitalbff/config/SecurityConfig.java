package cl.barriodigital.barriodigitalbff.config;

import cl.barriodigital.barriodigitalbff.security.AudienceValidator;
import cl.barriodigital.barriodigitalbff.security.AzureJwtAuthenticationConverter;
import cl.barriodigital.barriodigitalbff.security.JwtAccessDeniedHandler;
import cl.barriodigital.barriodigitalbff.security.JwtAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(BarriodigitalProperties.class)
public class SecurityConfig {

    private static final String REQUIRED_SCOPE = "SCOPE_access_as_user";

    private final AzureJwtAuthenticationConverter jwtAuthenticationConverter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            AzureJwtAuthenticationConverter jwtAuthenticationConverter,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
            .cors(cors -> { })
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/api/bff/health", "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/bff/me").hasAuthority(REQUIRED_SCOPE)

                    .requestMatchers(HttpMethod.PUT, "/api/requests/*/status")
                    .access(scopeAndRoles("Admin", "Operador"))

                    .requestMatchers(HttpMethod.GET, "/api/requests", "/api/requests/**")
                    .access(scopeAndRoles("Admin", "Operador", "Cliente"))

                    .requestMatchers(HttpMethod.POST, "/api/requests", "/api/requests/**")
                    .access(scopeAndRoles("Admin", "Operador", "Cliente"))

                    .requestMatchers(HttpMethod.GET, "/api/catalog", "/api/catalog/**")
                    .access(scopeAndRoles("Admin", "Operador", "Cliente"))

                    .requestMatchers(HttpMethod.POST, "/api/catalog", "/api/catalog/**")
                    .access(scopeAndRoles("Admin"))

                    .requestMatchers(HttpMethod.PUT, "/api/catalog", "/api/catalog/**")
                    .access(scopeAndRoles("Admin"))

                    .requestMatchers(HttpMethod.GET, "/api/report", "/api/report/**")
                    .access(scopeAndRoles("Admin"))

                    .requestMatchers(HttpMethod.GET, "/api/audit", "/api/audit/**")
                    .access(scopeAndRoles("Admin", "Auditor"))

                    .requestMatchers("/api/audit", "/api/audit/**").denyAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

    return http.build();
}

    @Bean
    JwtDecoder jwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            BarriodigitalProperties barriodigitalProperties) {
        String issuerUri = resourceServerProperties.getJwt().getIssuerUri();
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(resolveJwkSetUri(issuerUri)).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator =
                new AudienceValidator(barriodigitalProperties.getSecurity().getAudience());

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return jwtDecoder;
    }

    private String resolveJwkSetUri(String issuerUri) {
        String normalizedIssuer = issuerUri.endsWith("/")
                ? issuerUri.substring(0, issuerUri.length() - 1)
                : issuerUri;

        if (normalizedIssuer.endsWith("/v2.0")) {
            normalizedIssuer = normalizedIssuer.substring(0, normalizedIssuer.length() - "/v2.0".length());
        }

        return normalizedIssuer + "/discovery/v2.0/keys";
    }

    private AuthorizationManager<RequestAuthorizationContext> scopeAndRoles(String... roles) {
        return AuthorizationManagers.allOf(
                AuthorityAuthorizationManager.hasAuthority(REQUIRED_SCOPE),
                AuthorityAuthorizationManager.hasAnyRole(roles));
    }
}

