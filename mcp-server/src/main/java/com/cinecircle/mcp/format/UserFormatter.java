package com.cinecircle.mcp.format;

import com.cinecircle.mcp.client.dto.Comment;
import com.cinecircle.mcp.client.dto.UserProfile;
import com.cinecircle.mcp.client.dto.VoteResponse;
import org.springframework.stereotype.Component;

/** Users, votes and comments as compact text. */
@Component
public class UserFormatter {

    private final Links links;

    public UserFormatter(Links links) {
        this.links = links;
    }

    public String profile(UserProfile u) {
        StringBuilder sb = new StringBuilder("@").append(u.username());
        if (!Text.isBlank(u.displayName())) {
            sb.append(" (").append(Text.clip(u.displayName(), 50)).append(')');
        }
        sb.append(" · ").append(u.isPrivate() ? "private account" : "public account").append('\n');
        if (!Text.isBlank(u.bio())) {
            sb.append("Bio: ").append(Text.clip(u.bio(), Text.MAX_BIO)).append('\n');
        }
        sb.append(Text.plural(u.followerCount(), "follower", "followers"))
                .append(" · ").append(u.followingCount()).append(" following")
                .append(" · ").append(Text.plural(u.postCount(), "review", "reviews"))
                .append(" · joined ").append(Text.date(u.createdAt())).append('\n');
        if (u.followStatus() != null) {
            sb.append("Your follow status: ").append(u.followStatus()).append('\n');
        }
        sb.append(u.canViewPosts() ? "You can see their reviews." : "Their reviews are private (follow them and wait for approval to see them).")
                .append('\n');
        sb.append("Link: ").append(links.user(u.username()));
        return sb.toString();
    }

    public String upvoted(String postId, VoteResponse v) {
        return "Upvoted. The review now has " + Text.plural(v.upvoteCount(), "upvote", "upvotes") + ".\nLink: "
                + links.post(postId);
    }

    public String commented(String postId, Comment c) {
        return "Comment posted (id " + c.id() + ").\nLink: " + links.post(postId);
    }
}
