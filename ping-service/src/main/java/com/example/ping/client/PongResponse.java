package com.example.ping.client;

import org.springframework.http.HttpStatusCode;

public record PongResponse(HttpStatusCode status, String body, String errorMessage) {

    public boolean isError() {
        return errorMessage != null;
    }

    public boolean isSuccess() {
        return status != null && status.is2xxSuccessful();
    }

    public boolean isThrottled() {
        return status != null && status.value() == 429; // 或使用 HttpStatus.TOO_MANY_REQUESTS
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getBody() {
        return body;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}