package com.example.pong.web

import org.springframework.http.HttpStatus
import spock.lang.Specification

class PongControllerSpec extends Specification {

    def controller = new PongController()

    def "responds with World"() {
        when:
        def response = controller.hello().block()

        then:
        response.statusCode == HttpStatus.OK
        response.body == "World"
    }
}