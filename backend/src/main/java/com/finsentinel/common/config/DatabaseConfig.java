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

        // Auto-heal known Supabase pooler routing for project jcpbjzywqzghkcmxobni
        // Uses the IPv4 pooler cluster aws-1-ap-south-1.pooler.supabase.com:5432
        if (url != null && (url.contains("pooler.supabase.com") || url.contains("jcpbjzywqzghkcmxobni") || (username != null && username.contains("jcpbjzywqzghkcmxobni")))) {
            log.info("Auto-routing to Supabase IPv4 pooler aws-1-ap-south-1.pooler.supabase.com:5432 for tenant jcpbjzywqzghkcmxobni");
            url = "jdbc:postgresql://aws-1-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require";
            username = "finsentinel_app.jcpbjzywqzghkcmxobni";
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
