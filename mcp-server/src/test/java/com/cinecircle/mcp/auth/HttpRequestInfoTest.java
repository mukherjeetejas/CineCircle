package com.cinecircle.mcp.auth;

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The transport context extractor: token and client IP from ONE incoming MCP HTTP request. */
class HttpRequestInfoTest {

    private static HttpRequestInfo extract(MockHttpServletRequest servlet) {
        ServerRequest request = ServerRequest.create(servlet, List.of(new StringHttpMessageConverter()));
        McpTransportContext ctx = HttpRequestInfo.EXTRACTOR.extract(request);
        return (HttpRequestInfo) ctx.get(HttpRequestInfo.CONTEXT_KEY);
    }

    @Test
    void readsBearerToken_andRemoteAddress() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader("Authorization", "Bearer abc.def.ghi");
        req.setRemoteAddr("10.0.0.7");

        HttpRequestInfo info = extract(req);
        assertThat(info.bearerToken()).isEqualTo("abc.def.ghi");
        assertThat(info.clientIp()).isEqualTo("10.0.0.7");
        assertThat(info.toString()).doesNotContain("abc.def.ghi");
    }

    @Test
    void noHeader_meansNoToken() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        assertThat(extract(req).bearerToken()).isNull();
    }

    @Test
    void forwardedFor_firstEntryWins() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader("X-Forwarded-For", " 203.0.113.9 , 10.1.1.1");
        req.setRemoteAddr("10.0.0.7");
        assertThat(extract(req).clientIp()).isEqualTo("203.0.113.9");
    }

    @Test
    void bearerParsing() {
        assertThat(HttpRequestInfo.bearer("bearer  tok ")).isEqualTo("tok");
        assertThat(HttpRequestInfo.bearer("Bearer ")).isNull();
        assertThat(HttpRequestInfo.bearer("Basic dXNlcjpwYXNz")).isNull();
        assertThat(HttpRequestInfo.bearer(null)).isNull();
    }

    @Test
    void callerToString_neverShowsToken() {
        assertThat(new Caller("secret.tok", "1.2.3.4", true).toString()).doesNotContain("secret.tok");
    }
}
