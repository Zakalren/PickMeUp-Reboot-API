package dev.zakalren.pickmeup.product.exception;

import dev.zakalren.pickmeup.common.exception.ErrorResponse;
import dev.zakalren.pickmeup.product.ProductController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ProductNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PRODUCT_NOT_FOUND", exception.getMessage(), null));
    }
}
