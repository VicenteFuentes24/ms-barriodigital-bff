package cl.barriodigital.barriodigitalbff.service;

import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

import cl.barriodigital.barriodigitalbff.exception.DownstreamServiceException;
import cl.barriodigital.barriodigitalbff.security.AuthenticatedUserHeaders;
import cl.barriodigital.barriodigitalbff.security.JwtIdentityExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class DownstreamProxyService {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length");

    private static final Set<String> BLOCKED_INBOUND_HEADERS = Set.of(
            "authorization",
            AuthenticatedUserHeaders.USER_EMAIL_HEADER.toLowerCase(Locale.ROOT),
            AuthenticatedUserHeaders.USER_ID_HEADER.toLowerCase(Locale.ROOT),
            AuthenticatedUserHeaders.USER_ROLES_HEADER.toLowerCase(Locale.ROOT));

    private final RestClient restClient;
    private final JwtIdentityExtractor jwtIdentityExtractor;

    public DownstreamProxyService(RestClient restClient, JwtIdentityExtractor jwtIdentityExtractor) {
        this.restClient = restClient;
        this.jwtIdentityExtractor = jwtIdentityExtractor;
    }

    public ResponseEntity<byte[]> forward(
            String baseUrl,
            String serviceName,
            HttpServletRequest request,
            byte[] body) {
        URI targetUri = buildTargetUri(baseUrl, request);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        AuthenticatedUserHeaders authenticatedUserHeaders = jwtIdentityExtractor.currentUserHeaders();

        try {
            RestClient.RequestBodySpec requestSpec = restClient
                    .method(method)
                    .uri(targetUri)
                    .headers(headers -> {
                        copyRequestHeaders(request, headers);
                        authenticatedUserHeaders.applyTo(headers);
                    });

            RestClient.ResponseSpec responseSpec = hasBody(body)
                    ? requestSpec.body(body).retrieve()
                    : requestSpec.retrieve();

            ResponseEntity<byte[]> downstreamResponse = responseSpec
                    .onStatus(HttpStatusCode::isError, (downstreamRequest, downstreamResponseError) -> { })
                    .toEntity(byte[].class);

            return ResponseEntity
                    .status(downstreamResponse.getStatusCode())
                    .headers(copyResponseHeaders(downstreamResponse.getHeaders()))
                    .body(downstreamResponse.getBody());
        } catch (ResourceAccessException exception) {
            throw serviceUnavailable(serviceName, request, exception);
        } catch (RestClientException exception) {
            throw serviceUnavailable(serviceName, request, exception);
        }
    }

    private URI buildTargetUri(String baseUrl, HttpServletRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(trimTrailingSlash(baseUrl))
                .path(request.getRequestURI());

        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            builder.query(request.getQueryString());
        }

        return builder.build(true).toUri();
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }

        return value;
    }

    private boolean hasBody(byte[] body) {
        return body != null && body.length > 0;
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (isBlockedRequestHeader(headerName)) {
                continue;
            }

            headers.addAll(headerName, Collections.list(request.getHeaders(headerName)));
        }
    }

    private HttpHeaders copyResponseHeaders(HttpHeaders source) {
        HttpHeaders target = new HttpHeaders();
        source.forEach((name, values) -> {
            if (!isHopByHop(name)) {
                target.addAll(name, values);
            }
        });
        return target;
    }

    private boolean isBlockedRequestHeader(String headerName) {
        String normalizedHeaderName = headerName.toLowerCase(Locale.ROOT);
        return isHopByHop(normalizedHeaderName) || BLOCKED_INBOUND_HEADERS.contains(normalizedHeaderName);
    }

    private boolean isHopByHop(String headerName) {
        return HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }

    private DownstreamServiceException serviceUnavailable(
            String serviceName,
            HttpServletRequest request,
            Exception exception) {
        return new DownstreamServiceException(
                "El servicio de " + serviceName + " no está disponible.",
                request.getRequestURI(),
                exception);
    }
}
