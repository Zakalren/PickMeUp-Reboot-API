package dev.zakalren.pickmeup.user;

import dev.zakalren.pickmeup.config.SecurityConfig;
import dev.zakalren.pickmeup.user.dto.UserResponse;
import dev.zakalren.pickmeup.user.dto.UserSignupRequest;
import dev.zakalren.pickmeup.user.exception.DuplicateUserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@DisplayName("UserController slice test")
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("Signup successful test")
    void signup_success() throws Exception {
        // given
        UserSignupRequest request = new UserSignupRequest(
                "21-12345678",
                "password1234",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002,11, 8),
                "010-1234-5678"
        );

        UserResponse response = new UserResponse(
                1L,
                "21-12345678",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678",
                LocalDateTime.now()
        );

        given(userService.signup(any(UserSignupRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/1"))
                .andExpect(jsonPath("$.serviceNumber").value("21-12345678"))
                .andExpect(jsonPath("$.name").value("KIM"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("Signup validation failed test")
    void signup_validationFailed() throws Exception {
        // given
        String invalidRequest = """
                {
                    "serviceNumber": "",
                    "password": "1234",
                    "name": "KIM"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    @DisplayName("Signup duplicate test")
    void signup_duplicate() throws Exception {
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

        given(userService.signup(any(UserSignupRequest.class)))
                .willThrow(new DuplicateUserException("21-12345678"));

        // when & then
        mockMvc.perform(post("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USER"));
    }

    @Test
    @DisplayName("Unauthenticated /me call test")
    void unauthenticated_call() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated /me call test")
    void authenticated_call() throws Exception {
        // given
        UserResponse response = new UserResponse(
                1L,
                "21-12345678",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678",
                LocalDateTime.now()
        );

        given(userService.findByServiceNumber("21-12345678")).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/users/me")
                        .with(user("21-12345678").roles("ROLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceNumber").value("21-12345678"));
    }
}
