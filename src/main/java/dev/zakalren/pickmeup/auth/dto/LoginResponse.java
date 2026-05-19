package dev.zakalren.pickmeup.auth.dto;

public record LoginResponse(
        String serviceNumber,
        String message
) {
}
