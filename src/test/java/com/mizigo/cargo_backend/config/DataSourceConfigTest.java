package com.mizigo.cargo_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;

class DataSourceConfigTest {

    @Test
    void convertsRenderPostgresUrlAndUsesItsCredentials() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://localhost:5432/mizigo_db");
        properties.setUsername("postgres");
        properties.setPassword("local-password");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL",
                        "postgresql://cargo-user:encoded%40password@db.example:5432/cargo?sslmode=require");

        try (HikariDataSource dataSource = (HikariDataSource) new DataSourceConfig()
                .dataSource(properties, environment)) {
            assertEquals(
                    "jdbc:postgresql://db.example:5432/cargo?sslmode=require",
                    dataSource.getJdbcUrl());
            assertEquals("cargo-user", dataSource.getUsername());
            assertEquals("encoded@password", dataSource.getPassword());
        }
    }

    @Test
    void explicitDatasourceCredentialsOverrideRenderUrlCredentials() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://localhost:5432/mizigo_db");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgres://render-user:render-password@db.example/cargo")
                .withProperty("SPRING_DATASOURCE_USERNAME", "configured-user")
                .withProperty("SPRING_DATASOURCE_PASSWORD", "configured-password");

        try (HikariDataSource dataSource = (HikariDataSource) new DataSourceConfig()
                .dataSource(properties, environment)) {
            assertEquals("configured-user", dataSource.getUsername());
            assertEquals("configured-password", dataSource.getPassword());
        }
    }
}
