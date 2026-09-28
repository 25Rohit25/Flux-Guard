package dev.fluxguard.products;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductRepository products;
    public ProductController(ProductRepository products) { this.products = products; }

    public record ProductInput(@NotBlank @Size(max = 160) String name,
                               @NotBlank @Size(max = 2000) String description,
                               @NotNull @DecimalMin("0.01") BigDecimal price,
                               @Min(0) int stock) {}
    public record ProductView(Long id, String name, String description, BigDecimal price, int stock) {
        static ProductView of(Product p) { return new ProductView(p.id, p.name, p.description, p.price, p.stock); }
    }

    @GetMapping
    public List<ProductView> all() { return products.findAll().stream().map(ProductView::of).toList(); }

    @GetMapping("/{id}")
    public ProductView one(@PathVariable Long id) { return ProductView.of(find(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductView create(@Valid @RequestBody ProductInput input) {
        return ProductView.of(products.save(new Product(input.name().trim(), input.description().trim(),
            input.price(), input.stock())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductView update(@PathVariable Long id, @Valid @RequestBody ProductInput input) {
        Product p = find(id);
        p.name = input.name().trim();
        p.description = input.description().trim();
        p.price = input.price();
        p.stock = input.stock();
        return ProductView.of(products.save(p));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) { products.delete(find(id)); }

    private Product find(Long id) {
        return products.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
