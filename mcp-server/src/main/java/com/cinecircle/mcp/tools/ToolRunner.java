package com.cinecircle.mcp.tools;

import com.cinecircle.mcp.auth.Caller;
import com.cinecircle.mcp.auth.CallerResolver;
import com.cinecircle.mcp.auth.RateLimiter;
import com.cinecircle.mcp.config.CineCircleProperties;
import com.cinecircle.mcp.error.ApiUnreachableException;
import com.cinecircle.mcp.error.BackendWakingUpException;
import com.cinecircle.mcp.error.CineCircleApiException;
import com.cinecircle.mcp.error.ErrorMessages;
import com.cinecircle.mcp.error.ToolFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Shared wrapper for every tool: resolves the {@link Caller} of THIS call (token from the MCP request's
 * {@code Authorization} header, or {@code CINECIRCLE_TOKEN} when allowed), applies the per-IP rate limit, enforces
 * the token for write tools, maps backend errors to short {@link ToolFailure}s (MCP {@code isError=true}) and, as a
 * last line of defence, redacts tokens from any output.
 */
@Component
public class ToolRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolRunner.class);
    private static final String REDACTED = "[redacted]";

    private final CineCircleProperties props;
    private final CallerResolver callers;
    private final RateLimiter rateLimiter;

    public ToolRunner(CineCircleProperties props, CallerResolver callers, RateLimiter rateLimiter) {
        this.props = props;
        this.callers = callers;
        this.rateLimiter = rateLimiter;
    }

    /** Read tool: works with or without a token. */
    public String read(String tool, ToolContext context, Function<Caller, String> body) {
        Caller caller = admit(context);
        return run(tool, caller, body);
    }

    /** Write tool: refuses before any HTTP call when the caller has no token. */
    public String write(String tool, ToolContext context, Function<Caller, String> body) {
        Caller caller = admit(context);
        if (!caller.hasToken()) {
            throw new ToolFailure(caller.http() ? ErrorMessages.TOKEN_REQUIRED_HOSTED : ErrorMessages.TOKEN_REQUIRED);
        }
        return run(tool, caller, body);
    }

    private Caller admit(ToolContext context) {
        Caller caller = callers.resolve(context);
        long retryAfter = rateLimiter.tryAcquire(caller.clientIp());
        if (retryAfter > 0) {
            log.info("Rate limit hit for a client ({} calls/min)", rateLimiter.limit());
            throw new ToolFailure(ErrorMessages.rateLimited(rateLimiter.limit(), retryAfter));
        }
        return caller;
    }

    private String run(String tool, Caller caller, Function<Caller, String> body) {
        try {
            return redact(body.apply(caller), caller);
        } catch (ToolFailure e) {
            throw new ToolFailure(redact(e.getMessage(), caller));
        } catch (CineCircleApiException e) {
            log.debug("Tool {} failed: backend {} {}", tool, e.status(), e.code());
            throw new ToolFailure(redact(ErrorMessages.forApiError(e), caller));
        } catch (BackendWakingUpException e) {
            log.warn("Tool {} failed: CineCircle API still asleep after retry ({})", tool, e.detail());
            throw new ToolFailure(redact(ErrorMessages.forWakingUp(e), caller));
        } catch (ApiUnreachableException e) {
            log.warn("Tool {} failed: CineCircle API not reachable at {}", tool, e.apiUrl());
            throw new ToolFailure(redact(ErrorMessages.forUnreachable(e), caller));
        } catch (RuntimeException e) {
            log.warn("Tool {} failed unexpectedly: {}", tool, e.getClass().getSimpleName());
            throw new ToolFailure("CineCircle error INTERNAL_ERROR: the tool failed unexpectedly ("
                    + e.getClass().getSimpleName() + ").");
        }
    }

    private String redact(String text, Caller caller) {
        if (text == null) {
            return null;
        }
        String out = text;
        if (caller.hasToken()) {
            out = out.replace(caller.token(), REDACTED);
        }
        if (props.token() != null) {
            out = out.replace(props.token(), REDACTED);
        }
        return out;
    }

    /** Throws a VALIDATION_ERROR style tool failure for bad tool input (no HTTP call is made). */
    public static ToolFailure invalid(String message) {
        return new ToolFailure("CineCircle error VALIDATION_ERROR: " + message);
    }
}
