package com.mizigo.cargo_backend.config;

import java.net.URI;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class DataSourceConfig {

    @Bean
    DataSource dataSource(DataSourceProperties properties, Environment environment) {
        DataSourceBuilder<?> builder = properties.initializeDataSourceBuilder();
        String databaseUrl = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_URL"),
                environment.getProperty("JDBC_DATABASE_URL"),
                environment.getProperty("DATABASE_URL"));

        if (databaseUrl == null) {
            return builder.build();
        }

        if (databaseUrl.startsWith("jdbc:")) {
            return builder.url(databaseUrl).build();
        }

        URI uri;
        try {
            uri = URI.create(databaseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("The configured PostgreSQL database URL is invalid", exception);
        }

        if (!("postgres".equalsIgnoreCase(uri.getScheme())
                || "postgresql".equalsIgnoreCase(uri.getScheme()))
                || !StringUtils.hasText(uri.getHost())
                || !StringUtils.hasText(uri.getRawPath())
                || "/".equals(uri.getRawPath())) {
            throw new IllegalStateException(
                    "The database URL must be a JDBC URL or a PostgreSQL connection URL");
        }

        String host = uri.getHost();
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        String jdbcUrl = "jdbc:postgresql://" + host
                + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
                + uri.getRawPath()
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());

        builder.url(jdbcUrl);

        String usernameOverride = environment.getProperty("SPRING_DATASOURCE_USERNAME");
        String passwordOverride = environment.getProperty("SPRING_DATASOURCE_PASSWORD");
        String userInfo = uri.getUserInfo();
        if (StringUtils.hasText(usernameOverride)) {
            builder.username(usernameOverride);
        }
        if (StringUtils.hasText(passwordOverride)) {
            builder.password(passwordOverride);
        }
        if (userInfo != null) {
            int separator = userInfo.indexOf(':');
            String uriUsername = separator < 0 ? userInfo : userInfo.substring(0, separator);
            String uriPassword = separator < 0 ? null : userInfo.substring(separator + 1);
            if (!StringUtils.hasText(usernameOverride)) {
                builder.username(uriUsername);
            }
            if (uriPassword != null && !StringUtils.hasText(passwordOverride)) {
                builder.password(uriPassword);
            }
        }

        return builder.build();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
