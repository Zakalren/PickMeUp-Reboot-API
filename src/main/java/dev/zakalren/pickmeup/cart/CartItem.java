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
        @Index(name = "idx_cart_items_user", columnList = "user_id"),
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
    private Long quantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CartItem create(User user, Product product, Long quantity) {
        validateQuantity(quantity);
        CartItem cartItem = new CartItem();
        cartItem.user = user;
        cartItem.product = product;
        cartItem.quantity = quantity;
        return cartItem;
    }

    public void updateQuantity(Long quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    public void increaseQuantity(Long amount) {
        validateQuantity(this.quantity + amount);
        this.quantity += amount;
    }

    public void decreaseQuantity(Long amount) {
        validateQuantity(this.quantity - amount);
        this.quantity -= amount;
    }

    private static void validateQuantity(Long quantity) {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("Quantity must be equal or more than 1.");
        }
    }
}
