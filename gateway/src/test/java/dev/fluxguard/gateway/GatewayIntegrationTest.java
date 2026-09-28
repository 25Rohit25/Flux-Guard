package dev.fluxguard.gateway;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayIntegrationTest {
    @Autowired RouteDefinitionLocator routes;
    @Autowired WebTestClient client;

    @Test void routesAreRegistered() {
        Set<String> ids = routes.getRouteDefinitions().map(r -> r.getId()).collectList().block()
            .stream().collect(Collectors.toSet());
        assertEquals(Set.of("auth", "users", "products", "orders"), ids);
    }

    @Test void protectedRouteRejectsAnonymousTraffic() {
        client.get().uri("/api/products").exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().exists("X-Request-ID")
            .expectBody().jsonPath("$.status").isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
}
