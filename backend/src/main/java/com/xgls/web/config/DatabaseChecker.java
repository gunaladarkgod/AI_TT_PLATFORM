package com.xgls.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Component
public class DatabaseChecker {
    @Value("${spring.datasource.url}")
    private String jdbcUrl;
    @Value("${spring.datasource.username}")
    private String username;
    @Value("${spring.datasource.password}")
    private String password;

    private final LettuceConnectionFactory lettuceConnectionFactory;

    public DatabaseChecker(LettuceConnectionFactory lettuceConnectionFactory) {
        this.lettuceConnectionFactory = lettuceConnectionFactory;
    }

    public boolean isMySqlConnected() {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isRedisConnected() {
        try {
            lettuceConnectionFactory.getConnection().ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}