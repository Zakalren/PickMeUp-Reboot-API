package dev.zakalren.pickmeup.user.exception;

public class DuplicateUserException extends RuntimeException {
    public DuplicateUserException(String serviceNumber) {
        super("이미 가입된 군번입니다. serviceNumber=" + serviceNumber);
    }
}
