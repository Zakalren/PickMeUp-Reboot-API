package dev.zakalren.pickmeup.product.dto;

import dev.zakalren.pickmeup.product.Product;

import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        String imageUrl,
        Integer price,
        String category,
        Integer stock,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getImageUrl(),
                product.getPrice(),
                product.getCategory(),
                product.getStock(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
