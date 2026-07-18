package dev.zakalren.pickmeup.order.exception;

public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("Cannot place an order: the cart is empty.");
    }
}
