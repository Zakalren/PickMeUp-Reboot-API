package dev.zakalren.pickmeup.auth;

import dev.zakalren.pickmeup.auth.dto.LoginRequest;
import dev.zakalren.pickmeup.config.SecurityConfig;
import dev.zakalren.pickmeup.user.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 필터의 버킷은 SecurityConfig 인스턴스(= 캐시된 컨텍스트) 단위로 공유되므로,
// 다른 테스트 클래스와 간섭하지 않도록 이 클래스 전용 IP(10.0.0.x)만 사용한다.
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("Login rate limit filter test")
public class LoginRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private ResultActions attemptLogin(String ip) throws Exception {
        return attemptLogin(ip, URI.create("/api/auth/login"));
    }

    private ResultActions attemptLogin(String ip, URI uri) throws Exception {
        LoginRequest request = new LoginRequest("21-12345678", "wrongpassword");
        return mockMvc.perform(post(uri)
                .with(req -> {
                    req.setRemoteAddr(ip);
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    @Test
    @DisplayName("Sixth failed attempt is rate limited test")
    void sixthFailure_rateLimited() throws Exception {
        // given
        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));

        // when: 같은 IP에서 5회 실패
        for (int i = 0; i < 5; i++) {
            attemptLogin("10.0.0.1").andExpect(status().isUnauthorized());
        }

        // then: 6번째는 인증 시도 없이 429, Retry-After는 refill 주기(1분) 이내
        attemptLogin("10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andExpect(result -> {
                    String retryAfter = result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
                    assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
                });
    }

    @Test
    @DisplayName("URL-encoded login path is also rate limited test")
    void encodedPath_alsoLimited() throws Exception {
        // given: %6C = 'l' — 디코딩 전 원본 URI로 매칭하면 이 변형이 필터만 우회한다
        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));
        URI encodedPath = URI.create("/api/auth/%6Cogin");

        // when: 인코딩된 경로로 5회 실패 (컨트롤러에는 정상 라우팅됨)
        for (int i = 0; i < 5; i++) {
            attemptLogin("10.0.0.5", encodedPath).andExpect(status().isUnauthorized());
        }

        // then: 같은 IP의 정규 경로도 이미 한도 소진 상태여야 함
        attemptLogin("10.0.0.5").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Buckets are isolated per client IP test")
    void otherIp_notAffected() throws Exception {
        // given: 한 IP가 한도를 소진
        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));
        for (int i = 0; i < 5; i++) {
            attemptLogin("10.0.0.2").andExpect(status().isUnauthorized());
        }
        attemptLogin("10.0.0.2").andExpect(status().isTooManyRequests());

        // when & then: 다른 IP는 여전히 인증 시도가 가능(401)
        attemptLogin("10.0.0.3").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Successful logins do not consume the bucket test")
    void successfulLogins_notLimited() throws Exception {
        // given
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "21-12345678",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        given(authenticationManager.authenticate(any())).willReturn(authentication);

        // when & then: 실패만 카운트하므로 성공은 한도를 넘게 반복해도 통과
        for (int i = 0; i < 10; i++) {
            attemptLogin("10.0.0.4").andExpect(status().isOk());
        }
    }
}
