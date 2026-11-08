# CineCircle MCP Server

Lets AI clients (Claude Code, Claude Desktop, MCP Inspector and other MCP clients) search movies, read
reviews and AI summaries and, with your token, post reviews, upvote and comment as your account.

It talks to CineCircle **only through the REST API** (`CINECIRCLE_API_URL`). It never touches MongoDB,
TMDB or the LLM, so every visibility, ownership and movie-validation rule is still enforced by the
backend: Claude sees exactly what your account can see in the web app.

- Java 21, Spring Boot 3.5.7, Spring AI 1.1.8 (`spring-ai-starter-mcp-server-webmvc`)
- Port **8090**
- **MCP endpoint (Streamable HTTP): `http://localhost:8090/mcp`**
- `stdio` Spring profile for Claude Desktop

## Run

The backend must be running on 8080 (see the root `README.md`).

```bash
cd mcp-server && mvn spring-boot:run
```

Or build the jar and run it:

```bash
cd mcp-server
mvn package
java -jar target/cinecircle-mcp-0.0.1-SNAPSHOT.jar
```

In IntelliJ: open `mcp-server/pom.xml` as a project (or add it as a Maven module) and run
`com.cinecircle.mcp.CineCircleMcpApplication`. Set the env vars in the run configuration.

Tests: `mvn test` (no backend needed; uses `MockRestServiceServer`).

## Environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `CINECIRCLE_API_URL` | no | `http://localhost:8080/api` | CineCircle REST API base URL |
| `CINECIRCLE_TOKEN` | no | — | Your CineCircle access token (JWT). Sent as `Authorization: Bearer` on every call. Needed for the write tools. Never logged and never shown in tool output. |

Without a token only the read tools work, and you see public content only. The write tools answer
"Set CINECIRCLE_TOKEN to post as your account."

PowerShell (current session):

```powershell
$env:CINECIRCLE_TOKEN = "eyJ..."
$env:CINECIRCLE_API_URL = "http://localhost:8080/api"   # optional
```

## Getting a token

Use **your own** token. Claude then acts as your account. Treat it like a password; it expires
after 7 days.

- **Web app:** log in at http://localhost:5173, open **Settings → "Use CineCircle with Claude"** and
  click **Copy my access token**.
- **API:** call the login endpoint and copy `token` from the response:

  ```bash
  curl -s -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"usernameOrEmail":"alice","password":"password123"}'
  ```

  PowerShell:

  ```powershell
  (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
     -ContentType "application/json" `
     -Body '{"usernameOrEmail":"alice","password":"password123"}').token
  ```

## Claude Code (HTTP)

Start the server (with `CINECIRCLE_TOKEN` set in its environment if you want to post), then:

```bash
claude mcp add --transport http cinecircle http://localhost:8090/mcp
```

Check with `claude mcp list`. In HTTP mode the token is read by the **server** process, not by
Claude Code, so set `CINECIRCLE_TOKEN` before `mvn spring-boot:run` / `java -jar`.

## Claude Desktop (stdio)

Build the jar first (`cd mcp-server && mvn package`), then add this to `claude_desktop_config.json`
(Windows: `%APPDATA%\Claude\claude_desktop_config.json`, macOS:
`~/Library/Application Support/Claude/claude_desktop_config.json`). Replace `<path-to>` with the
absolute path of your checkout and `<paste your token>` with your token:

```json
{
  "mcpServers": {
    "cinecircle": {
      "command": "java",
      "args": [
        "-jar",
        "<path-to>/mcp-server/target/cinecircle-mcp-0.0.1-SNAPSHOT.jar",
        "--spring.profiles.active=stdio"
      ],
      "env": {
        "CINECIRCLE_API_URL": "http://localhost:8080/api",
        "CINECIRCLE_TOKEN": "<paste your token>"
      }
    }
  }
}
```

Windows example path (use `/` or `\\` in JSON): `C:/Users/you/movie reviews app/mcp-server/target/cinecircle-mcp-0.0.1-SNAPSHOT.jar`.

Restart Claude Desktop. In stdio mode the server runs no web server, prints no banner and writes
nothing but MCP messages to stdout; logs go to `<java.io.tmpdir>/cinecircle-mcp.log`
(Windows: `%TEMP%\cinecircle-mcp.log`).

## MCP Inspector

```bash
npx @modelcontextprotocol/inspector
```

Choose transport **Streamable HTTP**, URL `http://localhost:8090/mcp`, connect, then **List Tools**.

## Tools

Read tools (always available):

| Tool | Params | Backend call |
|---|---|---|
| `search_movies` | `query` (required) | `GET /movies/search?q=` (first page) |
| `get_trending_movies` | — | `GET /movies/trending` |
| `get_movie` | `tmdbId` | `GET /movies/{tmdbId}` |
| `get_movie_reviews` | `tmdbId`, `sort` (`top`\|`new`, default `top`), `limit` (1–20, default 10), `includeSpoilers` (default false) | `GET /movies/{tmdbId}/posts` |
| `get_review_summary` | `tmdbId` | `GET /movies/{tmdbId}/summary` (+ `GET /movies/{tmdbId}` for the title; falls back to the id if that fails) |
| `get_popular_reviews` | `window` (`day`\|`week`\|`all`, default `week`), `limit` (1–20, default 10) | `GET /posts/popular` |
| `get_user_profile` | `username` | `GET /users/{username}` |

Write tools (need `CINECIRCLE_TOKEN`):

| Tool | Params | Backend call |
|---|---|---|
| `create_review` | `tmdbId`, `title`, `body`, `rating` (1–10), `containsSpoilers` (default false) | `POST /posts` (multipart, `imageType=TMDB_POSTER`) |
| `upvote_review` | `postId` | `PUT /posts/{id}/upvote` |
| `comment_on_review` | `postId`, `body` | `POST /posts/{id}/comments` |

Output is compact text. Reviews show author, rating, title, review text up to 300 characters (a
preview; the link opens the full review), upvotes and a link `http://localhost:5173/posts/{id}`.

### Spoilers

Each review gets a tag from its `spoiler` object: `[spoilers – flagged by author]`,
`[spoilers – flagged by AI: <reason>]` (both when the author and the AI flagged it) or
`[spoiler check pending]`. Unless `includeSpoilers=true`, a review that is flagged or still
pending shows `[spoilers hidden]` instead of its body, and instead of its title when the AI said
the title gives something away. If a review has no `spoiler` object, the author's own
`containsSpoilers` flag decides (author tag + hidden). `get_popular_reviews` always hides spoilers. `create_review`
reports the AI spoiler check result of the new review.

### Errors

Backend errors come back as tool errors that include the backend code, e.g.
`CineCircle error MOVIE_NOT_FOUND: ...`, `NOT_ENOUGH_REVIEWS`, `UNAUTHORIZED` (token missing or
expired). If the backend is down: `CineCircle API is not reachable at http://localhost:8080/api`.
