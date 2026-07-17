package com.pulse.poller;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

final class PollerExceptionClassifier {

    private PollerExceptionClassifier() {
    }

    static boolean shouldBackoff(RuntimeException exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == HttpStatus.TOO_MANY_REQUESTS.value() || status >= 500;
        }
        return false;
    }

    static Duration retryAfter(RuntimeException exception) {
        if (!(exception instanceof RestClientResponseException responseException)) {
            return null;
        }
        String value = responseException.getResponseHeaders() == null
                ? null
                : responseException.getResponseHeaders().getFirst("Retry-After");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
