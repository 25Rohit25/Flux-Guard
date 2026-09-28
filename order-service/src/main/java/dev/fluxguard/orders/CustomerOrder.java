package dev.fluxguard.orders;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "user_id", nullable = false)
    public Long userId;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    public BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(nullable = false, length = 20)
    public String status = "CREATED";
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {}
    public CustomerOrder(Long userId) { this.userId = userId; }
    public void addItem(OrderItem item) { item.order = this; items.add(item); }
}
