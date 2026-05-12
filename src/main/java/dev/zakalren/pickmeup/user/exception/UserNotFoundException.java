package dev.zakalren.pickmeup.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String serviceNumber) {
        super("사용자를 찾을 수 없습니다. serviceNumber=" + serviceNumber);
    }
}
