package dev.zakalren.pickmeup.user;

public enum UserRole {
    USER, ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
