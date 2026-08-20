package com.example.ping.scheduler;

import com.example.ping.client.PongClient;
import com.example.ping.client.PongResponse;
import com.example.ping.ratelimit.FileLockRateLimiter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class PingScheduler implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PingScheduler.class);

    private final FileLockRateLimiter rateLimiter;
    private final PongClient pongClient;
    private final long intervalMillis;

    private Disposable disposable;

    public PingScheduler(FileLockRateLimiter rateLimiter,
                         PongClient pongClient,
                         @Value("${app.ping.interval-ms:1000}") long intervalMillis) {
        this.rateLimiter = rateLimiter;
        this.pongClient = pongClient;
        this.intervalMillis = intervalMillis;
    }

    @Override
    public void run(String... args) {
        disposable = Flux.interval(Duration.ofMillis(intervalMillis))
                .flatMap(tick -> ping())
                .subscribe(
                        this::logResult,
                        error -> log.error("Unexpected error", error)
                );
    }

    private Mono<PongResponse> ping() {
        return rateLimiter.tryAcquireAsync()
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.info("Result=RATE_LIMITED - Request not sent as being rate limited");
                        return Mono.empty();
                    }
                    return pongClient.send();
                });
    }

    private void logResult(PongResponse response) {
        if (response == null) return;
        if (response.isError()) {
            log.warn("Result=ERROR - {}", response.getErrorMessage());
        } else if (response.isSuccess()) {
            log.info("Result=SENT_AND_RESPONDED - Pong responded: {}", response.getBody());
        } else if (response.isThrottled()) {
            log.info("Result=SENT_AND_THROTTLED - Pong throttled it");
        } else {
            log.warn("Result=UNEXPECTED - status={}", response.getStatus());
        }
    }

    @PreDestroy
    public void stop() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }
}