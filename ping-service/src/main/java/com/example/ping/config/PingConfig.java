package com.example.ping.config;

import com.example.ping.ratelimit.FileLockRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Paths;

@Configuration
public class PingConfig {

    @Bean
    public WebClient pongWebClient(
            @Value("${app.pong.base-url:http://localhost:8081}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public FileLockRateLimiter fileLockRateLimiter(
            @Value("${app.rate-limit.lock-file}") String lockFile,
            @Value("${app.rate-limit.max-requests-per-second:2}") int maxRequestsPerSecond) {
        return new FileLockRateLimiter(Paths.get(lockFile), maxRequestsPerSecond);
    }
}