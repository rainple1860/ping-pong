package com.example.ping.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class PongClient {

    private final WebClient webClient;
    private final String path;

    public PongClient(WebClient pongWebClient,
                      @Value("${app.pong.path:/hello}") String path) {
        this.webClient = pongWebClient;
        this.path = path;
    }

    public Mono<PongResponse> send() {
        return webClient.get()
                .uri(path)
                .retrieve()
                .toEntity(String.class)
                .map(response -> new PongResponse(response.getStatusCode(), response.getBody(), null))
                .onErrorResume(WebClientResponseException.class, e ->
                        Mono.just(new PongResponse(e.getStatusCode(), e.getResponseBodyAsString(), null))
                )
                .onErrorResume(Exception.class, e ->
                        Mono.just(new PongResponse(null, null, e.getMessage()))
                );
    }
}