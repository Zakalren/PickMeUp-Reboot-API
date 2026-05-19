package dev.zakalren.pickmeup.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String serviceNumber,
        @NotBlank String password
) {
}
