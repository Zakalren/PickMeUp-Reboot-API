package dev.zakalren.pickmeup.order.exception;

import dev.zakalren.pickmeup.common.exception.ErrorResponse;
import dev.zakalren.pickmeup.order.OrderController;
import dev.zakalren.pickmeup.product.exception.InsufficientStockException;
import dev.zakalren.pickmeup.user.exception.UserNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Scoped to the order controller (an unscoped advice applies globally).
// @Order(1) keeps this advice ahead of GlobalExceptionHandler's catch-all.
@Order(1)
@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ORDER_NOT_FOUND", exception.getMessage()));
    }

    // 400 (not 409): an empty cart is not a state another request could have
    // raced us into resolving — the caller simply has nothing to order
    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<ErrorResponse> handleEmptyCart(EmptyCartException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("EMPTY_CART", exception.getMessage()));
    }

    // 409: same reasoning as the cart endpoints — the request is well-formed
    // and may succeed once inventory changes
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(InsufficientStockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("INSUFFICIENT_STOCK", exception.getMessage()));
    }

    // A vanished session user surfaces here, mirroring CartExceptionHandler
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("USER_NOT_FOUND", exception.getMessage()));
    }
}
