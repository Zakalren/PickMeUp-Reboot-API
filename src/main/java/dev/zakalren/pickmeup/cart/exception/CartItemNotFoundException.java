package dev.zakalren.pickmeup.cart.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(Long id) {
        super("Could not find cart item with id=" + id);
    }
}
