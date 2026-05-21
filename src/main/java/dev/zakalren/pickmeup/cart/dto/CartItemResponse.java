package dev.zakalren.pickmeup.cart.dto;

import dev.zakalren.pickmeup.cart.CartItem;

import java.time.LocalDateTime;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        String productImageUrl,
        Integer productPrice,
        Integer quantity,
        Long totalPrice,
        LocalDateTime createdAt
) {

    public static CartItemResponse from(CartItem cartItem) {
        return new CartItemResponse(
                cartItem.getId(),
                cartItem.getProduct().getId(),
                cartItem.getProduct().getName(),
                cartItem.getProduct().getImageUrl(),
                cartItem.getProduct().getPrice(),
                cartItem.getQuantity(),
                (long) cartItem.getProduct().getPrice() * cartItem.getQuantity(),
                cartItem.getCreatedAt()
        );
    }
}
