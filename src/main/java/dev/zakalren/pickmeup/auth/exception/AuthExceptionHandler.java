package dev.zakalren.pickmeup.auth.exception;

import dev.zakalren.pickmeup.common.GlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    // Avoid leaking whether the user exists (preventing enumeration attack)
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<GlobalExceptionHandler.ErrorResponse> handleBadCredentials(Exception exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new GlobalExceptionHandler.ErrorResponse("INVALID_CREDENTIALS", "군번 또는 비밀번호가 올바르지 않습니다.", null));
    }
}
