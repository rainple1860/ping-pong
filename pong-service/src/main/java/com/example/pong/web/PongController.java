package com.example.pong.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class PongController {

    @GetMapping("/hello")
    public Mono<ResponseEntity<String>> hello() {
        return Mono.just(ResponseEntity.ok("World"));
    }
}