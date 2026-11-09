package com.cinecircle.mcp.tools;

import com.cinecircle.mcp.client.CineCircleApiClient;
import com.cinecircle.mcp.format.PlainTextResultConverter;
import com.cinecircle.mcp.format.UserFormatter;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** User tools: get_user_profile. */
@Component
public class UserTools {

    private final CineCircleApiClient api;
    private final UserFormatter format;
    private final ToolRunner runner;

    public UserTools(CineCircleApiClient api, UserFormatter format, ToolRunner runner) {
        this.api = api;
        this.format = format;
        this.runner = runner;
    }

    @Tool(name = "get_user_profile", resultConverter = PlainTextResultConverter.class, description = """
            Get a CineCircle user's public profile by username: display name, bio, follower / following / \
            review counts, whether the account is private, your follow status and whether you can see their \
            reviews. Use it when the user asks about a reviewer.""")
    public String getUserProfile(@ToolParam(description = "CineCircle username, e.g. \"alice\" (a leading @ is ignored)") String username,
            ToolContext toolContext) {
        return runner.read("get_user_profile", toolContext, caller -> {
            String u = ToolArgs.required("username", username);
            if (u.startsWith("@")) {
                u = ToolArgs.required("username", u.substring(1));
            }
            return format.profile(api.user(caller, u));
        });
    }
}
