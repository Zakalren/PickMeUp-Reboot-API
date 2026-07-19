package dev.zakalren.pickmeup.user;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.zakalren.pickmeup.common.exception.ErrorResponse;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;

/**
 * Abuse protection for signup: a token bucket per client IP that consumes a
 * token on every request, unlike LoginRateLimitFilter which only counts
 * failures. A genuine new user signs up once, so there is no legitimate
 * high-frequency caller to protect here — and both a successful signup and a
 * DUPLICATE_USER failure reveal whether a serviceNumber is already taken
 * (UserService checks existence before hashing, so only the success path
 * actually costs a BCrypt hash), so both must be throttled the same way to
 * close the enumeration channel regardless of which outcome is cheaper.
 */
public class SignupRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(10);

    // Match on the decoded path exactly like Security/MVC routing does, for
    // the same reason as LoginRateLimitFilter (see its comment).
    private final RequestMatcher signupRequestMatcher =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/users/signup");

    private final ObjectMapper objectMapper;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(100_000)
            .build();

    public SignupRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !signupRequestMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Bucket bucket = buckets.get(request.getRemoteAddr(), ip -> newBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            reject(response, probe.getNanosToWaitForRefill());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(MAX_ATTEMPTS)
                        .refillGreedy(MAX_ATTEMPTS, REFILL_PERIOD))
                .build();
    }

    private void reject(HttpServletResponse response, long nanosToWait) throws IOException {
        long retryAfterSeconds = Math.max(1, Duration.ofNanos(nanosToWait).plusNanos(999_999_999).toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.of("SIGNUP_RATE_LIMITED",
                        "회원가입 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.")));
    }
}
