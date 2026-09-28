package dev.fluxguard.products;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, length = 160)
    public String name;
    @Column(nullable = false, length = 2000)
    public String description;
    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal price;
    @Column(nullable = false)
    public int stock;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    protected Product() {}
    public Product(String name, String description, BigDecimal price, int stock) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
