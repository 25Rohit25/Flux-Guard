package dev.fluxguard.orders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class OrderControllerTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderController controller = new OrderController(orders, "http://localhost:8082");

    private JwtAuthenticationToken principal(String id, String role) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(id)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test void orderOwnerIsEnforced() {
        CustomerOrder order = new CustomerOrder(42L);
        order.id = 7L;
        when(orders.findById(7L)).thenReturn(Optional.of(order));
        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
            () -> controller.one(7L, principal("11", "USER")));
        assertEquals(HttpStatus.NOT_FOUND, denied.getStatusCode());
        assertEquals(42L, controller.one(7L, principal("42", "USER")).userId());
        assertEquals(42L, controller.one(7L, principal("11", "ADMIN")).userId());
    }
}
