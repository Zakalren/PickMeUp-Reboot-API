package dev.zakalren.pickmeup.user;

import dev.zakalren.pickmeup.config.SecurityConfig;
import dev.zakalren.pickmeup.user.dto.UserResponse;
import dev.zakalren.pickmeup.user.dto.UserSignupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 필터의 버킷은 SecurityConfig 인스턴스(= 캐시된 컨텍스트) 단위로 공유되므로,
// 다른 테스트 클래스와 간섭하지 않도록 이 클래스 전용 IP(10.0.1.x)만 사용한다.
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@DisplayName("Signup rate limit filter test")
public class SignupRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private ResultActions attemptSignup(String ip) throws Exception {
        UserSignupRequest request = new UserSignupRequest(
                "21-12345678",
                "password1234",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678"
        );
        return mockMvc.perform(post("/api/users/signup")
                .with(req -> {
                    req.setRemoteAddr(ip);
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    @Test
    @DisplayName("11th attempt is rate limited test")
    void eleventhAttempt_rateLimited() throws Exception {
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
        given(userService.signup(any(UserSignupRequest.class))).willReturn(response);

        // when: 같은 IP에서 10회 요청 (성공/실패 관계없이 매 요청이 토큰을 소비)
        for (int i = 0; i < 10; i++) {
            attemptSignup("10.0.1.1").andExpect(status().isCreated());
        }

        // then: 11번째는 서비스 호출 없이 429, Retry-After는 refill 주기(10분) 이내
        attemptSignup("10.0.1.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SIGNUP_RATE_LIMITED"))
                .andExpect(result -> {
                    String retryAfter = result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
                    assertThat(Long.parseLong(retryAfter)).isBetween(1L, 600L);
                });
    }

    @Test
    @DisplayName("Buckets are isolated per client IP test")
    void otherIp_notAffected() throws Exception {
        // given: 한 IP가 한도를 소진
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

        for (int i = 0; i < 10; i++) {
            attemptSignup("10.0.1.2").andExpect(status().isCreated());
        }
        attemptSignup("10.0.1.2").andExpect(status().isTooManyRequests());

        // when & then: 다른 IP는 여전히 회원가입이 가능(201)
        attemptSignup("10.0.1.3").andExpect(status().isCreated());
    }
}
