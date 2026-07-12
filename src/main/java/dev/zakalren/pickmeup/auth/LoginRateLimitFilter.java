package dev.zakalren.pickmeup.auth;

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
 * Brute-force protection for the login endpoint: a token bucket per client
 * IP where only failed attempts (401 responses) consume tokens.
 *
 * Counting failures instead of requests means an active legitimate user is
 * never locked out, and keying by IP instead of by account means an attacker
 * cannot deny service to a victim's account by spamming wrong passwords.
 * The buckets are in-memory, which is sufficient for the current
 * single-instance deployment; horizontal scaling would need a shared
 * backend (e.g. bucket4j-redis).
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_FAILURES = 5;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    // Match on the decoded path exactly like Security/MVC routing does; a
    // raw getRequestURI() string comparison is bypassable with a
    // percent-encoded variant (/api/auth/%6Cogin) that still routes to the
    // controller but would skip this filter
    private final RequestMatcher loginRequestMatcher =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login");

    private final ObjectMapper objectMapper;

    // Bounded cache so the map cannot grow with distinct IPs forever; an
    // evicted idle bucket would have refilled anyway, so dropping it is safe
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public LoginRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !loginRequestMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // Behind the prod reverse proxy this is the real client address:
        // forward-headers-strategy: native restores it from X-Forwarded-For
        Bucket bucket = buckets.get(request.getRemoteAddr(), ip -> newBucket());

        // Consume atomically up front and refund non-failures below: a
        // peek-then-consume-later check would admit a whole concurrent
        // burst before the first token is ever spent
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            reject(response, probe.getNanosToWaitForRefill());
            return;
        }

        filterChain.doFilter(request, response);

        // Refund everything except failed logins so only failures count.
        // All authentication failures currently map to 401 (see
        // AuthExceptionHandler); addTokens saturates at capacity.
        if (response.getStatus() != HttpStatus.UNAUTHORIZED.value()) {
            bucket.addTokens(1);
        }
    }

    private static Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(MAX_FAILURES)
                        .refillGreedy(MAX_FAILURES, REFILL_PERIOD))
                .build();
    }

    private void reject(HttpServletResponse response, long nanosToWait) throws IOException {
        long retryAfterSeconds = Math.max(1, Duration.ofNanos(nanosToWait).plusNanos(999_999_999).toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.of("LOGIN_RATE_LIMITED",
                        "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.")));
    }
}
