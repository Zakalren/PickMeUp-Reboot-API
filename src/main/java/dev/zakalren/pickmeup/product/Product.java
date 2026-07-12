package dev.zakalren.pickmeup.product;

import dev.zakalren.pickmeup.product.exception.InsufficientStockException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name="image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false, length = 50)
    private String category;

    // Available inventory. Cart quantities may not exceed it (enforced by
    // CartItem); actual decrement is deferred until an order domain exists.
    @Column(nullable = false)
    private Integer stock;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Product create(String name, String imageUrl, Integer price, String category, Integer stock) {
        Product product = new Product();
        product.name = name;
        product.imageUrl = imageUrl;
        product.price = price;
        product.category = category;
        product.stock = stock;
        return product;
    }

    public void update(String name, String imageUrl, Integer price, String category, Integer stock) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.price = price;
        this.category = category;
        this.stock = stock;
    }

    public void validateStockAvailable(int requested) {
        if (requested > stock) {
            throw new InsufficientStockException(id, requested, stock);
        }
    }

}
