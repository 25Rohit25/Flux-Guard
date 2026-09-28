package dev.fluxguard.gateway;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration
public class TrafficFilter {
    private static final Logger log = LoggerFactory.getLogger(TrafficFilter.class);

    @Bean DefaultRedisScript<Long> tokenBucketScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("rate-limit.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean @Order(-200) WebFilter trafficLogging() {
        return (exchange, chain) -> {
            String id = UUID.randomUUID().toString();
            long start = System.nanoTime();
            exchange.getResponse().getHeaders().set("X-Request-ID", id);
            ServerWebExchange updated = exchange.mutate().request(exchange.getRequest().mutate()
                .headers(headers -> headers.set("X-Request-ID", id)).build()).build();
            return chain.filter(updated).doFinally(signal -> {
                long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
                int status = updated.getResponse().getStatusCode() == null ? 500 :
                    updated.getResponse().getStatusCode().value();
                log.info("requestId={} method={} path={} status={} latencyMs={}", id,
                    updated.getRequest().getMethod(), updated.getRequest().getPath(), status, millis);
            });
        };
    }

    @Bean GlobalFilter distributedRateLimit(ReactiveStringRedisTemplate redis, DefaultRedisScript<Long> script,
                                             MeterRegistry registry,
                                             @Value("${fluxguard.rate.replenish:10}") int replenish,
                                             @Value("${fluxguard.rate.burst:100}") int burst) {
        if (replenish < 1 || burst < 1) throw new IllegalArgumentException("Rate parameters must be positive");
        return new OrderedFilter(-50) {
            @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                if (route == null) return chain.filter(exchange);
                String ip = exchange.getRequest().getRemoteAddress() == null ? "unknown" :
                    exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
                return exchange.getPrincipal().map(Principal::getName).defaultIfEmpty("ip:" + ip)
                    .flatMap(key -> redis.execute(script, List.of("rate:" + route.getId() + ":" + key),
                            Integer.toString(replenish), Integer.toString(burst)).next()
                        .map(value -> Optional.of(value == 1L))
                        .switchIfEmpty(Mono.just(Optional.empty()))
                        .onErrorResume(error -> {
                            log.warn("Rate limiter unavailable: {}", error.toString());
                            return Mono.just(Optional.empty());
                        }))
                    .flatMap(outcome -> {
                        if (outcome.isEmpty())
                            return error(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Traffic control unavailable");
                        if (outcome.get()) return chain.filter(exchange);
                        registry.counter("fluxguard.rate_limited", "route", route.getId()).increment();
                        exchange.getResponse().getHeaders().set("Retry-After", "1");
                        return error(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
                    });
            }
        };
    }

    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String message) {
        if (exchange.getResponse().isCommitted()) return Mono.empty();
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}")
            .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private abstract static class OrderedFilter implements GlobalFilter, Ordered {
        private final int order;
        OrderedFilter(int order) { this.order = order; }
        @Override public int getOrder() { return order; }
    }
}
