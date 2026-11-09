package com.cinecircle.common;

import com.cinecircle.config.AppProperties;
import com.cinecircle.config.ImageProperties;
import com.cinecircle.config.LimitsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T23:59:30Z"));

    private RateLimiter limiter(int posts, int comments, int register, int login) {
        return new RateLimiter(new LimitsProperties(posts, comments, register, login), clock);
    }

    @Test
    void postsResetAtMidnightUtc() {
        RateLimiter rl = limiter(2, 50, 5, 10);
        rl.acquirePost("u1");
        rl.acquirePost("u1");
        assertThatThrownBy(() -> rl.acquirePost("u1"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(ex.getStatus().value()).isEqualTo(429);
                    assertThat(ex.getMessage()).isEqualTo("You've posted 2 reviews today — come back tomorrow!");
                });
        rl.acquirePost("u2"); // per user
        clock.set(Instant.parse("2026-11-10T00:00:00Z"));
        rl.acquirePost("u1");
    }

    @Test
    void releaseGivesTheSlotBack() {
        RateLimiter rl = limiter(1, 1, 1, 10);
        rl.acquireComment("u");
        rl.releaseComment("u");
        rl.acquireComment("u");
        assertThatThrownBy(() -> rl.acquireComment("u")).hasMessage("You've posted 1 comment today — come back tomorrow!");
        rl.acquireRegister("1.2.3.4");
        rl.releaseRegister("1.2.3.4");
        rl.acquireRegister("1.2.3.4");
        assertThatThrownBy(() -> rl.acquireRegister("1.2.3.4")).isInstanceOf(ApiException.class);
    }

    @Test
    void loginWindowIsOneMinuteAndRegisterOneHour() {
        clock.set(Instant.parse("2026-11-09T10:00:00Z"));
        RateLimiter rl = limiter(10, 50, 1, 2);
        rl.acquireLogin("ip");
        rl.acquireLogin("ip");
        assertThatThrownBy(() -> rl.acquireLogin("ip")).isInstanceOf(ApiException.class);
        rl.acquireRegister("ip");
        clock.set(Instant.parse("2026-11-09T10:01:00Z"));
        rl.acquireLogin("ip");
        assertThatThrownBy(() -> rl.acquireRegister("ip")).isInstanceOf(ApiException.class);
        clock.set(Instant.parse("2026-11-09T11:00:00Z"));
        rl.acquireRegister("ip");
    }

    @Test
    void zeroOrNegativeLimitDisablesIt() {
        RateLimiter rl = limiter(0, -1, 0, 0);
        for (int i = 0; i < 100; i++) {
            rl.acquirePost("u");
            rl.acquireComment("u");
            rl.acquireLogin("ip");
        }
    }

    @Test
    void sweepDropsEndedWindows() {
        RateLimiter rl = limiter(5, 5, 5, 5);
        rl.acquireLogin("a");
        rl.acquirePost("u");
        clock.set(clock.instant().plusSeconds(120)); // minute window ended, day window too (crossed midnight)
        rl.sweep();
        assertThat(rl.size()).isZero();
    }

    @Test
    void clientIpIsFirstForwardedForEntryElseRemoteAddress() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("10.0.0.5");
        assertThat(ClientIp.of(r)).isEqualTo("10.0.0.5");
        r.addHeader("X-Forwarded-For", " 203.0.113.1 , 10.0.0.1, 10.0.0.2");
        assertThat(ClientIp.of(r)).isEqualTo("203.0.113.1");
        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setRemoteAddr("10.0.0.6");
        blank.addHeader("X-Forwarded-For", " ");
        assertThat(ClientIp.of(blank)).isEqualTo("10.0.0.6");
    }

    @Test
    void corsOriginsAreSplitTrimmedAndNormalised() {
        AppProperties props = new AppProperties("http://localhost:8080", null, null, false,
                new AppProperties.Cors(List.of("https://a.pages.dev/ ", " http://localhost:5173,https://b.dev")));
        assertThat(com.cinecircle.config.SecurityConfig.allowedOrigins(props))
                .containsExactly("https://a.pages.dev", "http://localhost:5173", "https://b.dev");
    }

    @Test
    void imageTooLargeMessageFollowsConfig() {
        assertThat(new ImageProperties(1048576, 300).tooLargeMessage()).isEqualTo("Image must be under 1 MB");
        assertThat(new ImageProperties(5242880, 300).tooLargeMessage()).isEqualTo("Image must be under 5 MB");
        assertThat(new ImageProperties(1572864, 300).tooLargeMessage()).isEqualTo("Image must be under 1.5 MB");
    }

    @Test
    void multipartLimitExceededIsContract400() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new ImageProperties(1048576, 300));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/posts");
        ResponseEntity<ErrorResponse> res = handler.handleTooLarge(new MaxUploadSizeExceededException(10), req);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(res.getBody().message()).isEqualTo("Image must be under 1 MB");
        assertThat(res.getBody().fieldErrors()).containsKey("image");
        MockHttpServletRequest avatar = new MockHttpServletRequest("PUT", "/api/users/me/avatar");
        assertThat(handler.handleTooLarge(new MaxUploadSizeExceededException(10), avatar).getBody().fieldErrors())
                .containsKey("file");
    }

    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
