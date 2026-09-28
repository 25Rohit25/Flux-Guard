package dev.fluxguard.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderRepository orders;
    private final RestClient products;

    public OrderController(OrderRepository orders, @Value("${fluxguard.product-url}") String productUrl) {
        this.orders = orders;
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(1000);
        requests.setReadTimeout(2000);
        this.products = RestClient.builder().baseUrl(productUrl).requestFactory(requests).build();
    }

    public record ItemInput(@NotNull @Min(1) Long productId, @Min(1) int quantity) {}
    public record OrderInput(@NotEmpty List<@Valid ItemInput> items) {}
    public record ProductSnapshot(Long id, BigDecimal price, int stock) {}
    public record ItemView(Long productId, int quantity, BigDecimal price) {}
    public record OrderView(Long id, Long userId, BigDecimal totalAmount, String status, List<ItemView> items) {
        static OrderView of(CustomerOrder order) {
            return new OrderView(order.id, order.userId, order.totalAmount, order.status,
                order.items.stream().map(i -> new ItemView(i.productId, i.quantity, i.price)).toList());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView create(@Valid @RequestBody OrderInput input, JwtAuthenticationToken principal,
                            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (input.items().stream().map(ItemInput::productId).distinct().count() != input.items().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate product in order");
        CustomerOrder order = new CustomerOrder(Long.valueOf(principal.getToken().getSubject()));
        for (ItemInput item : input.items()) {
            ProductSnapshot product;
            try {
                product = products.get().uri("/products/{id}", item.productId())
                    .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().body(ProductSnapshot.class);
            } catch (HttpClientErrorException.NotFound e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown product: " + item.productId());
            } catch (RestClientException e) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service unavailable");
            }
            if (product == null || product.price() == null || item.quantity() > product.stock())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient stock or invalid product");
            order.addItem(new OrderItem(item.productId(), item.quantity(), product.price()));
            order.totalAmount = order.totalAmount.add(product.price().multiply(BigDecimal.valueOf(item.quantity())));
        }
        return OrderView.of(orders.save(order));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<OrderView> list(JwtAuthenticationToken principal) {
        return orders.findByUserIdOrderByCreatedAtDesc(Long.valueOf(principal.getToken().getSubject()))
            .stream().map(OrderView::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public OrderView one(@PathVariable Long id, JwtAuthenticationToken principal) {
        return OrderView.of(ownedOrder(id, principal));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void cancel(@PathVariable Long id, JwtAuthenticationToken principal) {
        CustomerOrder order = ownedOrder(id, principal);
        if (!order.status.equals("CREATED"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only new orders may be cancelled");
        order.status = "CANCELLED";
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderView updateStatus(@PathVariable Long id, @RequestBody StatusInput input) {
        if (input.status() == null || !List.of("CREATED", "FULFILLED", "CANCELLED").contains(input.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order status");
        CustomerOrder order = orders.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.status.equals("CREATED") && !order.status.equals(input.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already final");
        order.status = input.status();
        return OrderView.of(order);
    }
    public record StatusInput(String status) {}

    private CustomerOrder ownedOrder(Long id, JwtAuthenticationToken principal) {
        CustomerOrder order = orders.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        boolean admin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && !order.userId.toString().equals(principal.getToken().getSubject()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        return order;
    }
}
