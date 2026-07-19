package dev.zakalren.pickmeup.order.exception;

public class OrderAlreadyCancelledException extends RuntimeException {

    public OrderAlreadyCancelledException(Long orderId) {
        super("Order already cancelled: " + orderId);
    }
}
