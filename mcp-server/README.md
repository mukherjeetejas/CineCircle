# CineCircle MCP Server

Lets AI clients (Claude Code, Claude Desktop, MCP Inspector and other MCP clients) search movies, read
reviews and AI summaries and, with your token, post reviews, upvote and comment as your account.

It talks to CineCircle **only through the REST API** (`CINECIRCLE_API_URL`). It never touches MongoDB,
TMDB or the LLM, so every visibility, ownership and movie-validation rule is still enforced by the
backend: Claude sees exactly what your account can see in the web app.

- Java 21, Spring Boot 3.5.7, Spring AI 1.1.8 (`spring-ai-starter-mcp-server-webmvc`)
- Port **8090** (`PORT`)
- **MCP endpoint (Streamable HTTP): `http://localhost:8090/mcp`**
- `stdio` Spring profile for Claude Desktop
- Health check: `GET http://localhost:8090/health` → `200 {"status":"ok"}` (no auth)

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
| `PORT` | no | `8090` | HTTP port (Render sets it) |
| `CINECIRCLE_API_URL` | no | `http://localhost:8080/api` | CineCircle REST API base URL. Hosted: `https://<backend>.onrender.com/api` |
| `CINECIRCLE_WEB_URL` | no | `http://localhost:5173` | Web app base URL used for review / movie / user links in tool output. Hosted: `https://<project>.pages.dev` |
| `CINECIRCLE_TOKEN` | no | — | Your CineCircle access token (JWT) for local use. Sent as `Authorization: Bearer` when the caller didn't send its own token. Never logged and never shown in tool output. |
| `MCP_ALLOW_ENV_TOKEN` | no | `true` | Whether `CINECIRCLE_TOKEN` may be used at all. **Must be `false` on a hosted server** (the Docker image sets `false`). |
| `MCP_RATE_LIMIT_PER_MINUTE` | no | `60` | Tool calls per client IP per minute over HTTP (first `X-Forwarded-For` entry, else remote address). Over the limit → tool error `RATE_LIMITED: Slow down …`. `0` disables. |

Which token a tool call uses:

- **HTTP** (Claude Code, Inspector, hosted): the `Authorization: Bearer <token>` header of **that** MCP
  request, forwarded to the backend for that call only. Without a header: `CINECIRCLE_TOKEN` if
  `MCP_ALLOW_ENV_TOKEN=true`, otherwise read-only and the write tools answer
  "Add your CineCircle token: see Settings → Use CineCircle with Claude".
- **stdio** (Claude Desktop): `CINECIRCLE_TOKEN` (if `MCP_ALLOW_ENV_TOKEN=true`). Without it the write
  tools answer "Set CINECIRCLE_TOKEN to post as your account."

Without a token only the read tools work, and you see public content only.

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

## Claude Code (HTTP, local)

Start the server, then either send your token with each request:

```bash
claude mcp add --transport http cinecircle http://localhost:8090/mcp --header "Authorization: Bearer <your token>"
```

or set `CINECIRCLE_TOKEN` in the **server's** environment before `mvn spring-boot:run` / `java -jar`
and add it without a header:

```bash
claude mcp add --transport http cinecircle http://localhost:8090/mcp
```

Check with `claude mcp list`.

## Hosted (Render)

| | URL |
|---|---|
| MCP endpoint (Streamable HTTP) | `https://<mcp>.onrender.com/mcp` |
| Health check | `https://<mcp>.onrender.com/health` |
| Backend API it calls (`CINECIRCLE_API_URL`) | `https://<backend>.onrender.com/api` |
| Links in tool output (`CINECIRCLE_WEB_URL`) | `https://<project>.pages.dev` |

On the hosted server every caller uses **their own** token, sent in the `Authorization` header of
their MCP requests (`MCP_ALLOW_ENV_TOKEN=false`, no `CINECIRCLE_TOKEN`). Get your token in the web app
under **Settings → Use CineCircle with Claude**.

**Claude Code:**

```bash
claude mcp add --transport http cinecircle https://<mcp>.onrender.com/mcp --header "Authorization: Bearer <your token>"
```

**Read-only without a token:** the same command without `--header`:

```bash
claude mcp add --transport http cinecircle https://<mcp>.onrender.com/mcp
```

or, in Claude apps, add `https://<mcp>.onrender.com/mcp` as a custom connector if your plan supports
custom connectors. You can then search movies and read public reviews and summaries; the write
tools reply "Add your CineCircle token: see Settings → Use CineCircle with Claude".

**Note:** free hosting sleeps when idle, so the first call after a break may take **~1–2 minutes**
(both the MCP server and the backend wake up). The server waits up to 70 s for the backend and
retries once after 5 s; if it is still asleep the tool says
"CineCircle is waking up — try again in a minute."

### Docker

```bash
docker build -t cinecircle-mcp mcp-server
docker run -p 8090:8090 -e CINECIRCLE_API_URL=https://<backend>.onrender.com/api \
  -e CINECIRCLE_WEB_URL=https://<project>.pages.dev cinecircle-mcp
```

The image runs as a non-root user, is sized for 512 MB (`JAVA_TOOL_OPTIONS`) and sets
`MCP_ALLOW_ENV_TOKEN=false`.

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

Choose transport **Streamable HTTP**, URL `http://localhost:8090/mcp`, connect, then **List Tools**. To act as
your account, add the header `Authorization: Bearer <your token>` in the Inspector's authentication settings.

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

Write tools (need a token: the `Authorization` header over HTTP, or `CINECIRCLE_TOKEN` locally):

| Tool | Params | Backend call |
|---|---|---|
| `create_review` | `tmdbId`, `title`, `body`, `rating` (1–10), `containsSpoilers` (default false) | `POST /posts` (multipart, `imageType=TMDB_POSTER`) |
| `upvote_review` | `postId` | `PUT /posts/{id}/upvote` |
| `comment_on_review` | `postId`, `body` | `POST /posts/{id}/comments` |

Output is compact text. Reviews show author, rating, title, review text up to 300 characters (a
preview; the link opens the full review), upvotes and a link `<CINECIRCLE_WEB_URL>/posts/{id}` (locally `http://localhost:5173/posts/{id}`).

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
expired), `RATE_LIMITED` (slow down: more than `MCP_RATE_LIMIT_PER_MINUTE` calls per minute from your IP).

Backend asleep or down: backend calls use a 70 s read timeout. On a connect error or a gateway
502/503/504 (a proxy answer, not a CineCircle error such as `TMDB_UNAVAILABLE`) the call is retried
once after 5 s; if it still fails the tool says
`CineCircle is waking up — try again in a minute. (CineCircle API at <url>: not reachable)`.
`create_review` and `comment_on_review` are only retried when the request never reached the server
(connect error), so nothing is ever posted twice. A read timeout gives
`CineCircle API is not reachable at <url>`.
