package dev.zakalren.pickmeup.product.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super("Insufficient stock for product id=" + productId
                + ": requested " + requested + ", available " + available);
    }
}
