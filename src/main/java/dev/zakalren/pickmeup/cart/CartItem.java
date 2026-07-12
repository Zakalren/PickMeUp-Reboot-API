package dev.zakalren.pickmeup.cart;

import dev.zakalren.pickmeup.product.Product;
import dev.zakalren.pickmeup.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cart_items", indexes = {
        // The unique composite index's leftmost prefix already covers user_id-only lookups
        @Index(name = "idx_cart_items_user_product", columnList = "user_id, product_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    // Optimistic lock: concurrent quantity changes fail at commit (mapped to
    // 409 by CartExceptionHandler) instead of silently losing one update
    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Quantity invariants live here (architectural decision #4): a quantity
    // increase can never push a cart line above the product's current stock.
    // Stock is not reserved — lowering a product's stock leaves existing
    // lines as-is until their next mutation re-validates them.
    public static CartItem create(User user, Product product, Integer quantity) {
        validateQuantity(quantity);
        product.validateStockAvailable(quantity);
        CartItem cartItem = new CartItem();
        cartItem.user = user;
        cartItem.product = product;
        cartItem.quantity = quantity;
        return cartItem;
    }

    public void updateQuantity(Integer quantity) {
        validateQuantity(quantity);
        product.validateStockAvailable(quantity);
        this.quantity = quantity;
    }

    public void increaseQuantity(Integer amount) {
        int newQuantity = this.quantity + amount;
        validateQuantity(newQuantity);
        product.validateStockAvailable(newQuantity);
        this.quantity = newQuantity;
    }

    public void decreaseQuantity(Integer amount) {
        validateQuantity(this.quantity - amount);
        this.quantity -= amount;
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("Quantity must be equal or more than 1.");
        }
    }
}
