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
                "USER",
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
                .andExpect(jsonPath("$.code").value("DUPLICATE_USER"))
                // @JsonInclude(NON_NULL): 검증 오류가 아니면 fieldErrors가 노출되지 않아야 함
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("Malformed JSON request test")
    void signup_malformedJson() throws Exception {
        // when & then: 파싱 불가 JSON도 Boot 기본 포맷이 아닌 통일된 ErrorResponse로 응답
        mockMvc.perform(post("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("Unexpected exception test")
    void signup_unexpectedException() throws Exception {
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
                .willThrow(new RuntimeException("DB connection lost"));

        // when & then: 미처리 예외도 통일된 포맷으로, 내부 메시지는 노출하지 않음
        mockMvc.perform(post("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
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
                "USER",
                LocalDateTime.now()
        );

        given(userService.findByServiceNumber("21-12345678")).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/users/me")
                        .with(user("21-12345678").roles("ROLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceNumber").value("21-12345678"))
                .andExpect(jsonPath("$.role").value("USER"));
    }
}
