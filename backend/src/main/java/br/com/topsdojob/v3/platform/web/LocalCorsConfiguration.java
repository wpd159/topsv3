package br.com.topsdojob.v3.platform.web;

import java.util.Arrays;

import br.com.topsdojob.v3.platform.request.RequestIdContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LocalCorsConfiguration implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final boolean local;

    public LocalCorsConfiguration(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins,
            @Value("${app.env:nao_configurado}") String appEnv) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        this.local = "local".equalsIgnoreCase(appEnv == null ? "" : appEnv.trim());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!local || allowedOrigins.length == 0) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Accept", "Content-Type", "X-XSRF-TOKEN", RequestIdContext.HEADER_NAME)
                .exposedHeaders(RequestIdContext.HEADER_NAME)
                .allowCredentials(true)
                .maxAge(3600);
    }

    boolean localCredentialsEnabled() {
        return local && allowedOrigins.length > 0;
    }
}
