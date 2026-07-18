package dev.zakalren.pickmeup.order;

import dev.zakalren.pickmeup.cart.CartItem;
import dev.zakalren.pickmeup.product.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order", columnList = "order_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Nullable on purpose: the FK nulls out when the product is deleted
    // (ON DELETE SET NULL); the snapshots below keep the line readable.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer quantity;

    // Only Order.place builds items, so creation stays package-private
    static OrderItem from(Order order, CartItem cartItem) {
        OrderItem item = new OrderItem();
        item.order = order;
        item.product = cartItem.getProduct();
        item.productName = cartItem.getProduct().getName();
        item.price = cartItem.getProduct().getPrice();
        item.quantity = cartItem.getQuantity();
        return item;
    }
}
