package cl.barriodigital.barriodigitalbff.config;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient restClient(RestClient.Builder builder, BarriodigitalProperties properties) {
        var http = properties.getHttp();
        if (http.getConnectTimeoutMs() <= 0 || http.getReadTimeoutMs() <= 0) {
            throw new IllegalArgumentException("BFF HTTP connect/read timeouts must be positive milliseconds");
        }
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(http.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(http.getReadTimeoutMs()));
        return builder.requestFactory(ClientHttpRequestFactories.get(settings)).build();
    }
}
