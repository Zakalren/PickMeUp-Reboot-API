package dev.zakalren.pickmeup.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserSignupRequest(
        @NotBlank @Size(min = 4, max = 20) String serviceNumber,
        @NotBlank @Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다.") String password,
        @NotBlank @Size(max = 50) String name,
        @Size(max = 100) String affiliatedUnit,
        @Size(max = 20) String rank,
        @Past LocalDate dateOfBirth,
        @Pattern(regexp = "^[0-9-]{9,20}$", message = "올바른 전화번호 형식이 아닙니다.") String telNumber
) {
}
