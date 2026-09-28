package dev.fluxguard.orders;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    public CustomerOrder order;
    @Column(name = "product_id", nullable = false)
    public Long productId;
    @Column(nullable = false)
    public int quantity;
    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal price;

    protected OrderItem() {}
    public OrderItem(Long productId, int quantity, BigDecimal price) {
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
    }
}
