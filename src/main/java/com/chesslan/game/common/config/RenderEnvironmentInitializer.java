package com.chesslan.game.common.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class RenderEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        String databaseUrl = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("POSTGRES_URL")
        );
        if (databaseUrl == null) {
            return;
        }

        DatabaseConfig config = parseDatabaseUrl(databaseUrl);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", config.jdbcUrl());

        if (isBlank(environment.getProperty("SPRING_DATASOURCE_USERNAME"))
                && isBlank(environment.getProperty("DB_USERNAME"))
                && config.username() != null) {
            properties.put("spring.datasource.username", config.username());
        }
        if (isBlank(environment.getProperty("SPRING_DATASOURCE_PASSWORD"))
                && isBlank(environment.getProperty("DB_PASSWORD"))
                && config.password() != null) {
            properties.put("spring.datasource.password", config.password());
        }

        environment.getPropertySources().addFirst(new MapPropertySource("renderEnvironment", properties));
    }

    private DatabaseConfig parseDatabaseUrl(String databaseUrl) {
        String normalized = normalizeDatabaseUrl(databaseUrl);
        try {
            URI uri = URI.create(normalized);
            String rawUserInfo = uri.getRawUserInfo();
            String username = null;
            String password = null;
            if (rawUserInfo != null) {
                String[] segments = rawUserInfo.split(":", 2);
                username = decode(segments[0]);
                password = segments.length > 1 ? decode(segments[1]) : null;
            }

            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                    .append(uri.getHost())
                    .append(":")
                    .append(port)
                    .append(uri.getRawPath());
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                jdbcUrl.append("?").append(uri.getRawQuery());
            }

            return new DatabaseConfig(jdbcUrl.toString(), username, password);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid DATABASE_URL/POSTGRES_URL format for Render deployment", exception);
        }
    }

    private String normalizeDatabaseUrl(String databaseUrl) {
        if (databaseUrl.startsWith("postgres://")) {
            return "postgresql://" + databaseUrl.substring("postgres://".length());
        }
        return databaseUrl;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return isBlank(second) ? null : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DatabaseConfig(String jdbcUrl, String username, String password) {
    }
}
