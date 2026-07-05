package dev.zakalren.pickmeup.cart.exception;

public class CartItemConflictException extends RuntimeException {
    public CartItemConflictException(Long productId) {
        super("Cart item for product id=" + productId + " was modified concurrently. Retry the request.");
    }
}
