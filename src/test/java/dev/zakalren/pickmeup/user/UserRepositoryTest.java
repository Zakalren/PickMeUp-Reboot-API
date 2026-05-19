package dev.zakalren.pickmeup.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@DisplayName("UserRepository slice test")
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("findByServiceNumber successful test")
    void findByServiceNumber() {
        // given
        User user = User.create(
                "21-12345678",
                "$encodedpassword$",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678"
        );
        userRepository.save(user);

        // when
        Optional<User> found = userRepository.findByServiceNumber("21-12345678");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("KIM");
    }

    @Test
    @DisplayName("findByServiceNumber not found test")
    void findByServiceNumber_notFound() {
        // when
        Optional<User> found = userRepository.findByServiceNumber("99-12345678");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByServiceNumber test")
    void existsByServiceNumber() {
        // given
        User user = User.create(
                "21-12345678",
                "$encodedpassword$",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678"
        );
        userRepository.save(user);

        // when & then
        assertThat(userRepository.existsByServiceNumber("21-12345678")).isTrue();
        assertThat(userRepository.existsByServiceNumber("99-12345678")).isFalse();
    }

    @Test
    @DisplayName("Save duplicate serviceNumber test")
    void saveDuplicateServiceNumber() {
        // given
        User user = User.create(
                "21-12345678",
                "$encodedpassword$",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678"
        );
        userRepository.save(user);

        User duplicate = User.create(
                "21-12345678",
                "$encodedpassword2$",
                "LEE",
                "ROKA",
                "Private",
                LocalDate.of(1999, 11, 11),
                "010-5678-1234"
        );

        // when & then
        assertThrows(Exception.class, () -> userRepository.save(duplicate));
    }
}
