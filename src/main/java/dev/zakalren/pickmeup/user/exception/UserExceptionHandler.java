package dev.zakalren.pickmeup.user.exception;

import dev.zakalren.pickmeup.common.GlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<GlobalExceptionHandler.ErrorResponse> handleDuplicate(DuplicateUserException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new GlobalExceptionHandler.ErrorResponse("DUPLICATE_USER", exception.getMessage(), null));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<GlobalExceptionHandler.ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new GlobalExceptionHandler.ErrorResponse("USER_NOT_FOUND", exception.getMessage(), null));
    }
}
