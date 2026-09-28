package dev.fluxguard.gateway;

import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
public class FallbackController {
    @RequestMapping("/fallback/{service}")
    public ResponseEntity<Map<String, Object>> fallback(@PathVariable String service,
                                                         ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        HttpStatus status = causedByTimeout(cause) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.of("status", status.value(),
            "message", service + " service temporarily unavailable"));
    }

    private boolean causedByTimeout(Throwable error) {
        while (error != null) {
            if (error instanceof TimeoutException || error instanceof io.netty.handler.timeout.TimeoutException
                || error instanceof java.net.SocketTimeoutException) return true;
            error = error.getCause();
        }
        return false;
    }
}
