package dev.zakalren.pickmeup.user;

import dev.zakalren.pickmeup.user.dto.UserResponse;
import dev.zakalren.pickmeup.user.dto.UserSignupRequest;
import dev.zakalren.pickmeup.user.exception.DuplicateUserException;
import dev.zakalren.pickmeup.user.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Test")
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("Signup")
    class Signup {

        @Test
        @DisplayName("Signup successful test")
        void signup_success() {
            // given
            UserSignupRequest request = new UserSignupRequest(
                    "21-12345678",
                    "password1234",
                    "KIM",
                    "ROKAF",
                    "Private",
                    LocalDate.of(2002, 11, 8),
                    "010-1234-5678"
            );

            given(userRepository.existsByServiceNumber("21-12345678")).willReturn(false);
            given(passwordEncoder.encode("password1234")).willReturn("$encodedpassword$");

            User savedUser = User.create(
                    "21-12345678",
                    "$encodedpassword$",
                    "KIM",
                    "ROKAF",
                    "Private",
                    LocalDate.of(2002, 11, 8),
                    "010-1234-5678"
            );
            given(userRepository.save(any(User.class))).willReturn(savedUser);

            // when
            UserResponse response = userService.signup(request);

            // then
            assertThat(response.serviceNumber()).isEqualTo("21-12345678");
            assertThat(response.name()).isEqualTo("KIM");

            verify(passwordEncoder).encode("password1234");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("DuplicateUserException test")
        void signup_duplicateServiceNumber() {
            // given
            UserSignupRequest request = new UserSignupRequest(
                    "21-12345678",
                    "password1234",
                    "KIM",
                    null,
                    null,
                    null,
                    null
            );
            given(userRepository.existsByServiceNumber("21-12345678")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.signup(request))
                    .isInstanceOf(DuplicateUserException.class)
                    .hasMessageContaining("21-12345678");

            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Find by service number")
    class FindByServiceNumber {

        @Test
        @DisplayName("findByServiceNumber successful test")
        void findByServiceNumber_success() {
            // given
            User user = User.create(
                    "21-12345678",
                    "",
                    "KIM",
                    "ROKAF",
                    "Private",
                    LocalDate.of(2002, 11, 8),
                    "010-1234-5678"
            );
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            // when
            UserResponse response = userService.findByServiceNumber("21-12345678");

            // then
            assertThat(response.serviceNumber()).isEqualTo("21-12345678");
            assertThat(response.name()).isEqualTo("KIM");
        }

        @Test
        @DisplayName("UserNotFoundException test")
        void findByServiceNumber_notFound() {
            // given
            given(userRepository.findByServiceNumber("99-12345678"))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.findByServiceNumber("99-12345678"))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }
}
