package com.devpilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configures browser access for the versioned API. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    /**
     * Creates the web configuration.
     *
     * @param appProperties type-safe application settings
     */
    public WebConfig(AppProperties appProperties) {
        if (appProperties.cors() == null
                || appProperties.cors().allowedOrigins() == null
                || appProperties.cors().allowedOrigins().isEmpty()) {
            throw new IllegalStateException("app.cors.allowed-origins must contain at least one value");
        }
        this.appProperties = appProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(appProperties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
