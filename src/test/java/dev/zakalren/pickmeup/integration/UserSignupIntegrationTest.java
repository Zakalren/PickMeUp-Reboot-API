package dev.zakalren.pickmeup.integration;

import dev.zakalren.pickmeup.auth.dto.LoginRequest;
import dev.zakalren.pickmeup.user.dto.UserSignupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@DisplayName("Signup -> Login -> /me integration test")
public class UserSignupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    // 격리는 클래스 레벨 @Transactional 롤백이 담당 — deleteAll() 중복 호출 불필요

    @Test
    @DisplayName("Signup -> Login -> /me flow test")
    void fullFlowTest() throws Exception {
        // 1. Signup
        UserSignupRequest signupRequest = new UserSignupRequest(
                "21-12345678",
                "password1234",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678"
        );

        // SignupRateLimitFilter의 버킷은 IP당 공유되고, @SpringBootTest 컨텍스트는
        // 같은 프로파일/설정을 쓰는 다른 통합 테스트 클래스와도 캐시되어 공유되므로
        // (LoginRateLimitTest의 동일한 이유), 이 클래스 전용 IP만 사용해 다른
        // 클래스의 회원가입 호출과 한도를 나눠 쓰지 않게 한다.
        mockMvc.perform(post("/api/users/signup")
                    .with(req -> {
                        req.setRemoteAddr("10.0.2.1");
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // 2. Login (get session)
        LoginRequest loginRequest = new LoginRequest(
                "21-12345678",
                "password1234"
        );

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        // 3. Call /me with login session
        mockMvc.perform(get("/api/users/me")
                    .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceNumber").value("21-12345678"))
                .andExpect(jsonPath("$.name").value("KIM"))
                .andExpect(jsonPath("$.password").doesNotExist());

    }

    @Test
    @DisplayName("Call /me without login test")
    void me_withoutLogin() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Session id rotation on login test")
    void login_rotatesSessionId() throws Exception {
        // 1. Signup
        UserSignupRequest signupRequest = new UserSignupRequest(
                "21-12345678",
                "password1234",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678"
        );
        // SignupRateLimitFilter의 버킷은 IP당 공유되고, @SpringBootTest 컨텍스트는
        // 같은 프로파일/설정을 쓰는 다른 통합 테스트 클래스와도 캐시되어 공유되므로
        // (LoginRateLimitTest의 동일한 이유), 이 클래스 전용 IP만 사용해 다른
        // 클래스의 회원가입 호출과 한도를 나눠 쓰지 않게 한다.
        mockMvc.perform(post("/api/users/signup")
                    .with(req -> {
                        req.setRemoteAddr("10.0.2.1");
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // 2. 로그인 전에 이미 세션이 존재하는 상황 (세션 고정 공격 시나리오)
        MockHttpSession preLoginSession = new MockHttpSession();
        String preLoginId = preLoginSession.getId();

        LoginRequest loginRequest = new LoginRequest(
                "21-12345678",
                "password1234"
        );
        mockMvc.perform(post("/api/auth/login")
                    .session(preLoginSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        // 3. 로그인 성공 후에는 세션 id가 회전되어야 함
        assertThat(preLoginSession.getId()).isNotEqualTo(preLoginId);
    }

    @Test
    @DisplayName("Logout invalidates session test")
    void logout_invalidatesSession() throws Exception {
        // 1. Signup + Login
        UserSignupRequest signupRequest = new UserSignupRequest(
                "21-12345678",
                "password1234",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678"
        );
        // SignupRateLimitFilter의 버킷은 IP당 공유되고, @SpringBootTest 컨텍스트는
        // 같은 프로파일/설정을 쓰는 다른 통합 테스트 클래스와도 캐시되어 공유되므로
        // (LoginRateLimitTest의 동일한 이유), 이 클래스 전용 IP만 사용해 다른
        // 클래스의 회원가입 호출과 한도를 나눠 쓰지 않게 한다.
        mockMvc.perform(post("/api/users/signup")
                    .with(req -> {
                        req.setRemoteAddr("10.0.2.1");
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(
                "21-12345678",
                "password1234"
        );
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        // 2. Logout — 필터 체인(logoutUrl) 처리, 204 응답
        mockMvc.perform(post("/api/auth/logout")
                    .session(session))
                .andExpect(status().isNoContent());

        // 3. 세션이 실제로 무효화되어 재사용 불가해야 함
        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/users/me")
                    .session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with wrong password test")
    void login_wrongPassword() throws Exception {
        // 1. Signup
        UserSignupRequest signupRequest = new UserSignupRequest(
                "21-12345678",
                "password1234",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678"
        );
        // SignupRateLimitFilter의 버킷은 IP당 공유되고, @SpringBootTest 컨텍스트는
        // 같은 프로파일/설정을 쓰는 다른 통합 테스트 클래스와도 캐시되어 공유되므로
        // (LoginRateLimitTest의 동일한 이유), 이 클래스 전용 IP만 사용해 다른
        // 클래스의 회원가입 호출과 한도를 나눠 쓰지 않게 한다.
        mockMvc.perform(post("/api/users/signup")
                    .with(req -> {
                        req.setRemoteAddr("10.0.2.1");
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // 2. Login
        LoginRequest loginRequest = new LoginRequest(
                "21-12345678",
                "wrongpassword");
        mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
