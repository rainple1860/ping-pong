package com.example.ping.client

import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import spock.lang.Specification

class PongClientSpec extends Specification {

    def "returns success response"() {
        given:
        ExchangeFunction exchangeFunction = { request ->
            Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "text/plain")
                            .body("World")
                            .build()
            )
        }
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build()
        PongClient client = new PongClient(webClient, "/hello")

        when:
        PongResponse response = client.send().block()   // 关键：调用 .block() 获取结果

        then:
        response.status == HttpStatus.OK
        response.body == "World"
        !response.isError()
        response.isSuccess()
    }

    def "returns throttled response"() {
        given:
        ExchangeFunction exchangeFunction = { request ->
            Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).build())
        }
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build()
        PongClient client = new PongClient(webClient, "/hello")

        when:
        PongResponse response = client.send().block()

        then:
        response.status == HttpStatus.TOO_MANY_REQUESTS
        response.isThrottled()
    }

    def "returns error response when network fails"() {
        given:
        ExchangeFunction exchangeFunction = { request ->
            Mono.error(new RuntimeException("boom"))
        }
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build()
        PongClient client = new PongClient(webClient, "/hello")

        when:
        PongResponse response = client.send().block()   // 阻塞等待错误处理后的结果

        then:
        response.isError()
        response.errorMessage == "boom"
    }
}