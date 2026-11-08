package com.cinecircle.mcp.tools;

import com.cinecircle.mcp.config.CineCircleProperties;
import com.cinecircle.mcp.error.ApiUnreachableException;
import com.cinecircle.mcp.error.CineCircleApiException;
import com.cinecircle.mcp.error.ErrorMessages;
import com.cinecircle.mcp.error.ToolFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Shared wrapper for every tool: maps backend errors to short {@link ToolFailure}s (MCP {@code isError=true}),
 * enforces the token for write tools, and as a last line of defence redacts the token from any output.
 */
@Component
public class ToolRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolRunner.class);
    private static final String REDACTED = "[redacted]";

    private final CineCircleProperties props;

    public ToolRunner(CineCircleProperties props) {
        this.props = props;
    }

    /** Read tool: works with or without a token. */
    public String read(String tool, Supplier<String> body) {
        return run(tool, body);
    }

    /** Write tool: refuses before any HTTP call when no token is configured. */
    public String write(String tool, Supplier<String> body) {
        if (!props.hasToken()) {
            throw new ToolFailure(ErrorMessages.TOKEN_REQUIRED);
        }
        return run(tool, body);
    }

    private String run(String tool, Supplier<String> body) {
        try {
            return redact(body.get());
        } catch (ToolFailure e) {
            throw new ToolFailure(redact(e.getMessage()));
        } catch (CineCircleApiException e) {
            log.debug("Tool {} failed: backend {} {}", tool, e.status(), e.code());
            throw new ToolFailure(redact(ErrorMessages.forApiError(e)));
        } catch (ApiUnreachableException e) {
            log.warn("Tool {} failed: CineCircle API not reachable at {}", tool, e.apiUrl());
            throw new ToolFailure(redact(ErrorMessages.forUnreachable(e)));
        } catch (RuntimeException e) {
            log.warn("Tool {} failed unexpectedly: {}", tool, e.getClass().getSimpleName());
            throw new ToolFailure("CineCircle error INTERNAL_ERROR: the tool failed unexpectedly ("
                    + e.getClass().getSimpleName() + ").");
        }
    }

    private String redact(String text) {
        if (text == null || !props.hasToken()) {
            return text;
        }
        return text.replace(props.token(), REDACTED);
    }

    /** Throws a VALIDATION_ERROR style tool failure for bad tool input (no HTTP call is made). */
    public static ToolFailure invalid(String message) {
        return new ToolFailure("CineCircle error VALIDATION_ERROR: " + message);
    }
}
