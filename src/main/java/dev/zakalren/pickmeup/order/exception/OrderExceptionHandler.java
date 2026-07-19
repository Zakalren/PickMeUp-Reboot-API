package dev.zakalren.pickmeup.order.exception;

import dev.zakalren.pickmeup.common.exception.ErrorResponse;
import dev.zakalren.pickmeup.order.OrderController;
import dev.zakalren.pickmeup.product.exception.InsufficientStockException;
import dev.zakalren.pickmeup.user.exception.UserNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
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

    // 409: the order exists and is owned by the caller but is already CANCELLED
    // — re-cancelling is a state conflict, consistent with the conflict family
    // above (chosen over a silent 204 so double-cancel is explicit, not masked)
    @ExceptionHandler(OrderAlreadyCancelledException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyCancelled(OrderAlreadyCancelledException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ORDER_ALREADY_CANCELLED", exception.getMessage()));
    }

    // CartItem is @Version-mapped: a checkout racing a cart edit (or a
    // double-submitted checkout) can fail the versioned DELETE at commit —
    // that's a retryable conflict, not a 500
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ORDER_CONFLICT",
                        "The cart was modified concurrently. Retry the order."));
    }

    // A vanished session user surfaces here, mirroring CartExceptionHandler
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("USER_NOT_FOUND", exception.getMessage()));
    }
}
