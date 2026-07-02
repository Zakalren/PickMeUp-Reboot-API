package dev.zakalren.pickmeup.cart.exception;

import dev.zakalren.pickmeup.cart.CartItemController;
import dev.zakalren.pickmeup.common.exception.ErrorResponse;
import dev.zakalren.pickmeup.product.exception.ProductNotFoundException;
import dev.zakalren.pickmeup.user.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Scoped to the cart controller; an unscoped advice applies globally, and the
// IllegalArgumentException mapping below would mask bugs in other domains as 400s.
@RestControllerAdvice(assignableTypes = CartItemController.class)
public class CartExceptionHandler {

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CartItemNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("CART_ITEM_NOT_FOUND", exception.getMessage(), null));
    }

    // Cart endpoints surface other domains' not-found exceptions
    // (unknown product id, vanished session user), so declare them here.
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PRODUCT_NOT_FOUND", exception.getMessage(), null));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("USER_NOT_FOUND", exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", exception.getMessage(), null));
    }
}