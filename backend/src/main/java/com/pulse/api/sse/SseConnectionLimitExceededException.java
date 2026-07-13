package com.pulse.api.sse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** SSE 동시 연결 수 상한 초과. 클라이언트는 잠시 후 재연결한다. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class SseConnectionLimitExceededException extends RuntimeException {

    public SseConnectionLimitExceededException(int maxConnections) {
        super("SSE 동시 연결 수 상한(" + maxConnections + ")을 초과했다.");
    }
}
