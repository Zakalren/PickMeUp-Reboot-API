package dev.zakalren.pickmeup.user.dto;

import dev.zakalren.pickmeup.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String serviceNumber,
        String name,
        String affiliatedUnit,
        String rank,
        LocalDate dateOfBirth,
        String telNumber,
        String role,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getServiceNumber(),
                user.getName(),
                user.getAffiliatedUnit(),
                user.getRank(),
                user.getDateOfBirth(),
                user.getTelNumber(),
                user.getRole().name(),
                user.getCreatedAt()
        );
    }
}
