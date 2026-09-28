package dev.fluxguard.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
public class GatewaySecurity {
    @Bean SecretKey jwtKey(@Value("${fluxguard.jwt-secret}") String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET must decode to at least 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean ReactiveJwtDecoder jwtDecoder(SecretKey key) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("fluxguard"));
        return decoder;
    }

    @Bean SecurityWebFilterChain security(ServerHttpSecurity http, MeterRegistry metrics) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> java.util.List.of(
            new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(a -> a
                .pathMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                .pathMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.PUT, "/api/products/**", "/api/orders/*/status").hasRole("ADMIN")
                .pathMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                .pathMatchers(HttpMethod.GET, "/api/users/*").hasRole("ADMIN")
                .anyExchange().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(
                new ReactiveJwtAuthenticationConverterAdapter(converter))))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((exchange, error) -> {
                    metrics.counter("fluxguard.auth_failures").increment();
                    return reject(exchange, HttpStatus.UNAUTHORIZED, "Authentication required or token invalid");
                })
                .accessDeniedHandler((exchange, error) -> {
                    metrics.counter("fluxguard.authorization_denied").increment();
                    return reject(exchange, HttpStatus.FORBIDDEN, "Insufficient role");
                }))
            .build();
    }

    private Mono<Void> reject(org.springframework.web.server.ServerWebExchange exchange,
                              HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
