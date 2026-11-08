package com.cinecircle.mcp.format;

import com.cinecircle.mcp.client.dto.MovieDetail;
import com.cinecircle.mcp.client.dto.Page;
import com.cinecircle.mcp.client.dto.PostDetail;
import com.cinecircle.mcp.client.dto.PostMovie;
import com.cinecircle.mcp.client.dto.PostSummary;
import com.cinecircle.mcp.client.dto.ReviewSummary;
import com.cinecircle.mcp.client.dto.Spoiler;
import org.springframework.stereotype.Component;

import java.util.List;

/** Reviews, review lists, AI summaries and create-review results as compact text. */
@Component
public class ReviewFormatter {

    private final Links links;

    public ReviewFormatter(Links links) {
        this.links = links;
    }

    /** Output of get_movie_reviews. */
    public String movieReviews(long tmdbId, String sort, Page<PostSummary> page, boolean includeSpoilers) {
        List<PostSummary> items = page.itemsOrEmpty();
        if (items.isEmpty()) {
            return "No CineCircle reviews you can see for tmdbId " + tmdbId + " yet. Movie page: " + links.movie(tmdbId);
        }
        PostMovie m = items.get(0).movie();
        String movieName = m != null && m.title() != null ? m.title() + Text.year(m.releaseYear()) : "tmdbId " + tmdbId;
        StringBuilder sb = new StringBuilder()
                .append("Reviews of ").append(movieName).append(" (tmdbId ").append(tmdbId).append(") - ")
                .append(sort).append(", showing ").append(items.size()).append(":\n");
        boolean anyHidden = appendList(sb, items, includeSpoilers, false);
        appendFooter(sb, page.hasNext(), anyHidden, links.movie(tmdbId));
        return sb.toString().stripTrailing();
    }

    /** Output of get_popular_reviews (spoilers always hidden; the tool has no includeSpoilers param). */
    public String popularReviews(String window, Page<PostSummary> page) {
        List<PostSummary> items = page.itemsOrEmpty();
        if (items.isEmpty()) {
            return "No popular CineCircle reviews for window '" + window + "'.";
        }
        StringBuilder sb = new StringBuilder()
                .append("Popular reviews (").append(window).append("), showing ").append(items.size()).append(":\n");
        boolean anyHidden = appendList(sb, items, false, true);
        if (anyHidden) {
            sb.append("\nSome reviews contain spoilers and are hidden; open the link to read them.");
        }
        return sb.toString().stripTrailing();
    }

    /** Output of create_review: link + spoiler result. */
    public String created(PostDetail p) {
        StringBuilder sb = new StringBuilder("Posted. ");
        sb.append('"').append(Text.clip(p.title(), Text.MAX_TITLE)).append("\" - ").append(p.rating()).append("/10");
        if (p.movie() != null) {
            sb.append(" for ").append(p.movie().title()).append(Text.year(p.movie().releaseYear()));
        }
        sb.append("\nLink: ").append(links.post(p.id())).append('\n');
        sb.append(spoilerResult(p));
        return sb.toString();
    }

    /** Human sentence describing the spoiler check result of a newly created review. */
    static String spoilerResult(PostDetail p) {
        Spoiler s = p.spoiler();
        boolean author = p.containsSpoilers();
        String status = s == null || s.aiStatus() == null ? "UNCHECKED" : s.aiStatus();
        String authorNote = author ? " You marked it as containing spoilers, so it is blurred for readers anyway." : "";
        return switch (status) {
            case "SPOILER" -> {
                String reason = Text.isBlank(s.aiReason()) ? "" : " (" + Text.clip(s.aiReason(), 80) + ")";
                String title = s.titleHidden() ? " Its title is hidden too." : "";
                yield "Spoiler check: Our AI marked it as containing spoilers" + reason
                        + ", so it will be blurred for readers." + title + " Tag: " + SpoilerTags.tags(s, author);
            }
            case "CLEAN" -> "Spoiler check: Our AI found no spoilers." + authorNote;
            case "PENDING" -> "Spoiler check: still running, so the review is blurred for readers until it finishes."
                    + authorNote;
            default -> "Spoiler check: the AI check was skipped." + authorNote;
        };
    }

    /**
     * Output of get_review_summary. {@code movie} may be null (movie lookup failed): then only the tmdbId is named.
     */
    public String summary(ReviewSummary s, MovieDetail movie) {
        String name = movie == null || Text.isBlank(movie.title())
                ? "tmdbId " + s.tmdbId()
                : movie.title() + Text.year(movie.releaseYear()) + " · tmdbId " + s.tmdbId();
        StringBuilder sb = new StringBuilder()
                .append("What CineCircle thinks of ").append(name).append(" (AI-generated summary)\n")
                .append("Sentiment: ").append(s.sentiment())
                .append(" · avg rating ").append(Text.decimal(s.avgRating())).append("/10")
                .append(" · based on ").append(Text.plural(s.basedOnCount(), "review", "reviews"))
                .append(" · updated ").append(Text.dateTime(s.generatedAt())).append('\n')
                .append(Text.clip(s.summary(), 700)).append('\n');
        appendBullets(sb, "Pros", s.pros());
        appendBullets(sb, "Cons", s.cons());
        if (s.stale()) {
            sb.append("Note: this summary may be out of date.\n");
        }
        sb.append("Movie page: ").append(links.movie(s.tmdbId()));
        return sb.toString();
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private boolean appendList(StringBuilder sb, List<PostSummary> items, boolean includeSpoilers, boolean showMovie) {
        boolean anyHidden = false;
        int n = 1;
        for (PostSummary p : items) {
            Spoiler s = p.spoiler();
            boolean hideBody = SpoilerTags.hideBody(s, p.containsSpoilers(), includeSpoilers);
            boolean hideTitle = SpoilerTags.hideTitle(s, p.containsSpoilers(), includeSpoilers);
            anyHidden |= hideBody;

            String author = p.author() == null ? "unknown" : "@" + p.author().username();
            String title = hideTitle ? SpoilerTags.HIDDEN : '"' + Text.clip(p.title(), Text.MAX_TITLE) + '"';
            sb.append('\n').append(n++).append(". ").append(title)
                    .append(" - ").append(p.rating()).append("/10 by ").append(author)
                    .append(" · ").append(Text.plural(p.upvoteCount(), "upvote", "upvotes"))
                    .append(" · ").append(Text.plural(p.commentCount(), "comment", "comments"))
                    .append(" · ").append(Text.date(p.createdAt())).append('\n');
            if (showMovie && p.movie() != null) {
                sb.append("   Movie: ").append(p.movie().title()).append(Text.year(p.movie().releaseYear()))
                        .append(" · tmdbId ").append(p.movie().tmdbId()).append('\n');
            }
            String tags = SpoilerTags.tags(s, p.containsSpoilers());
            if (!tags.isEmpty()) {
                sb.append("   ").append(tags).append('\n');
            }
            sb.append("   ").append(hideBody ? SpoilerTags.HIDDEN : Text.clip(p.bodyPreview(), Text.MAX_PREVIEW)).append('\n');
            sb.append("   ").append(links.post(p.id())).append('\n');
        }
        return anyHidden;
    }

    private static void appendFooter(StringBuilder sb, boolean hasNext, boolean anyHidden, String movieLink) {
        if (anyHidden) {
            sb.append("\nSome reviews contain spoilers and are hidden. Call again with includeSpoilers=true only if "
                    + "the user wants spoilers.");
        }
        if (hasNext) {
            sb.append("\nMore reviews: ").append(movieLink);
        }
    }

    private static void appendBullets(StringBuilder sb, String label, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append(label).append(":\n");
        for (String it : items) {
            sb.append("- ").append(Text.clip(it, 120)).append('\n');
        }
    }
}
