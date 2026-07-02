package dev.zakalren.pickmeup.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_service_number", columnList = "service_number", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_number", nullable = false, unique = true, length = 20)
    private String serviceNumber;

    @Column(nullable = false, length = 255)
    private String encodedPassword;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "affiliated_unit", length = 100)
    private String affiliatedUnit;

    // RANK is a reserved word in MySQL 8, so the column needs an explicit name
    @Column(name = "military_rank", length = 20)
    private String rank;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "tel_number", length = 20)
    private String telNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static User create(
            String serviceNumber,
            String encodedPassword,
            String name,
            String affiliatedUnit,
            String rank,
            LocalDate dateOfBirth,
            String telNumber
    ) {
        User user = new User();
        user.serviceNumber = serviceNumber;
        user.encodedPassword = encodedPassword;
        user.name = name;
        user.affiliatedUnit = affiliatedUnit;
        user.rank = rank;
        user.dateOfBirth = dateOfBirth;
        user.telNumber = telNumber;
        return user;
    }
}
