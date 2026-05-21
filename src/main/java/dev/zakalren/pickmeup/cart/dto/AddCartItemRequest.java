package dev.zakalren.pickmeup.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull(message = "Product id must not be null.")
        Long productId,

        @NotNull(message = "Quantity must not be null.")
        @Min(value = 1, message = "Quantity must be equal or more than 1.")
        Long quantity
) {
}
