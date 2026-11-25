package com.finsentinel.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        String url = properties.getUrl();
        String username = properties.getUsername();
        String password = properties.getPassword();

        // Auto-heal known Supabase pooler routing mismatch for project jcpbjzywqzghkcmxobni
        if (url != null && (url.contains("pooler.supabase.com") || "postgres.jcpbjzywqzghkcmxobni".equals(username))) {
            log.info("Auto-correcting Supabase connection to direct verified host db.jcpbjzywqzghkcmxobni.supabase.co for tenant jcpbjzywqzghkcmxobni");
            url = "jdbc:postgresql://db.jcpbjzywqzghkcmxobni.supabase.co:5432/postgres?sslmode=require";
            username = "finsentinel_app";
            password = "FinSentinel2026!AppPass";
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(20000);
        return new HikariDataSource(config);
    }
}
