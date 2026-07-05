package dev.zakalren.pickmeup.cart.exception;

import dev.zakalren.pickmeup.cart.CartItemController;
import dev.zakalren.pickmeup.common.exception.ErrorResponse;
import dev.zakalren.pickmeup.product.exception.ProductNotFoundException;
import dev.zakalren.pickmeup.user.exception.UserNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Scoped to the cart controller; an unscoped advice applies globally, and the
// IllegalArgumentException mapping below would mask bugs in other domains as 400s.
// @Order(1) keeps this advice ahead of GlobalExceptionHandler's Exception catch-all.
@Order(1)
@RestControllerAdvice(assignableTypes = CartItemController.class)
public class CartExceptionHandler {

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CartItemNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("CART_ITEM_NOT_FOUND", exception.getMessage()));
    }

    // Cart endpoints surface other domains' not-found exceptions
    // (unknown product id, vanished session user), so declare them here.
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("PRODUCT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("USER_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(CartItemConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(CartItemConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CART_ITEM_CONFLICT", exception.getMessage()));
    }

    // @Version conflicts surface at transaction commit, i.e. when the service
    // proxy returns — they pass through the controller and land here.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CART_ITEM_CONFLICT",
                        "The cart item was modified concurrently. Retry the request."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_REQUEST", exception.getMessage()));
    }
}