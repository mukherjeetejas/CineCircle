# CineCircle v4 — Make It Deployable (free hosting, open sign-up, cost-safe)

Prepares the app to run on free hosting: **Cloudflare Pages** (frontend), **Render free web
services** (backend + MCP server, Docker, 512 MB RAM, sleeps after 15 min idle), **MongoDB Atlas
free cluster** (512 MB). Sign-up is **open to anyone**, so in-app limits protect the AI credits.

This file **extends** `CLAUDE.md`, `FEATURES-V2.md` and `SEED-DATA.md`. Config keys, env vars,
error codes, collections, Dockerfiles and endpoints below are **approved by the project owner**.
Where this file says it *replaces* an earlier rule, this file wins. Everything else still applies.
Anything beyond this file → stop and report.

Golden rule: **every setting comes from an env var with a local default**, so the app still runs
locally exactly as today with no env changes.

## Ownership
- `backend-dev` → everything for `backend/` below (incl. `backend/Dockerfile`, `backend/.dockerignore`).
- `frontend-dev` → everything for `frontend/` below.
- `mcp-dev` → everything for `mcp-server/` below (incl. its Dockerfile).
- `deploy-reviewer` (new) → read-only check of all of this.

---

## Backend

### Config (application.yml — all via env, local defaults)
| Key | Env var | Local default | Production value (set on Render) |
|---|---|---|---|
| `server.port` | `PORT` | 8080 | set by Render |
| `server.forward-headers-strategy` | — | `framework` | (needed behind Render's HTTPS proxy) |
| `app.public-base-url` | `APP_PUBLIC_BASE_URL` | `http://localhost:8080` | `https://<backend>.onrender.com` |
| `app.cors.origins` | `APP_CORS_ORIGINS` | `http://localhost:5173` | `https://<project>.pages.dev` (comma-separated list allowed) |
| `app.seed` | `APP_SEED` | true | **false** |
| `app.images.max-bytes` | `IMAGE_MAX_BYTES` | 5242880 | 1048576 |
| `app.images.storage-limit-mb` | `IMAGE_STORAGE_LIMIT_MB` | 300 | 300 |
| `app.llm.daily-call-limit` | `LLM_DAILY_CALL_LIMIT` | 150 | 150 |
| `app.limits.posts-per-day` | `LIMIT_POSTS_PER_DAY` | 10 | 10 |
| `app.limits.comments-per-day` | `LIMIT_COMMENTS_PER_DAY` | 50 | 50 |
| `app.limits.register-per-hour-per-ip` | `LIMIT_REGISTER_PER_HOUR` | 5 | 5 |
| `app.limits.login-per-minute-per-ip` | `LIMIT_LOGIN_PER_MINUTE` | 10 | 10 |

- **Replace every hard-coded `http://localhost:8080`** (image URLs etc.) with `app.public-base-url`.
  TMDB poster URLs are unchanged. `MONGODB_URI`, `JWT_SECRET`, `TMDB_API_TOKEN`,
  `CINECIRCLE_LLM_API_KEY` stay as they are.
- Update `.env.example` with every variable above (no real values).

### Health
- 🔓 `GET /api/health` → `200 {"status":"ok"}`; cheap, no DB call, no auth. Used by Render.

### Cost protection (new — open sign-up makes these essential)
1. **Daily AI call cap.** New collection `llm_usage` `{ date (UTC yyyy-MM-dd, unique), calls }`.
   `LlmClient` atomically `$inc`s today's doc before each call; if the result would exceed
   `app.llm.daily-call-limit`, it does **not** call Anthropic and behaves as disabled for that call.
   Effects: spoiler checks stay `PENDING` (the job retries them after midnight UTC — attempts are
   **not** used up by cap refusals); summaries return the cached copy (`stale: true`) or
   `502 SUMMARY_UNAVAILABLE`. Log once per day when the cap is hit.
2. **Summary regeneration (replaces the FEATURES-V2 caching rule).** Regenerate only when the
   eligible-post count differs from `basedOnCount` (any difference) **and** the cached copy is at
   least 1 hour old, or when no summary exists. Never regenerate just because time passed.
3. **Rate limits** (in-memory per instance, e.g. a simple token bucket or Bucket4j-free code —
   no new dependency): posts per user per day, comments per user per day, register per IP per
   hour, login per IP per minute. Client IP = first entry of `X-Forwarded-For` if present, else
   remote address. Exceeding → `429 RATE_LIMITED` (new error code) with a friendly `message`
   like "You've posted 10 reviews today — come back tomorrow!".
4. **Storage guard.** Uploads larger than `app.images.max-bytes` → `400 VALIDATION_ERROR`
   ("Image must be under 1 MB"). If total GridFS `images` size ≥ `storage-limit-mb` →
   `507 STORAGE_FULL` (new error code), so Atlas's 512 MB is never filled.

### Docker (`backend/Dockerfile`, `backend/.dockerignore`)
- Multi-stage: build with `maven:3.9-eclipse-temurin-21` (`mvn -q -DskipTests package`), run on
  `eclipse-temurin:21-jre`. Run as a non-root user. `EXPOSE 8080`.
- Memory for a 512 MB instance: `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k"`.
- `.dockerignore`: `target/`, `.idea/`, `*.iml`, `.env*`, logs.
- If Docker is installed locally: `docker build -t cinecircle-backend backend` must succeed.

### Tests
LLM cap (call refused at limit, counter per UTC day, cap refusal doesn't consume spoiler
attempts), summary regeneration rule, each rate limit → 429, storage guard → 507, image size →
400, health endpoint, CORS allows the configured origin and rejects others, image URLs use
`app.public-base-url`.

---

## Frontend

### Config (Vite env vars, read via `import.meta.env`)
| Var | Local default | Production (set on Cloudflare Pages) |
|---|---|---|
| `VITE_API_URL` | `/api` (Vite proxy, as today) | `https://<backend>.onrender.com/api` |
| `VITE_MCP_URL` | `http://localhost:8090/<path from mcp-server README>` | `https://<mcp>.onrender.com/<path>` |
| `VITE_MAX_IMAGE_MB` | 5 | 1 |

- `apiFetch` uses `VITE_API_URL`. Remove any other hard-coded `localhost` URL.
- Add `frontend/.env.example` documenting the three vars; make sure `.env.local` is git-ignored.
- SPA routing works on Cloudflare Pages without config as long as there is **no top-level
  `404.html`** in the build output — don't add one.

### Free-hosting UX
- **Wake-up banner:** if any API request is still pending after 4 s, show a slim non-blocking
  banner "☕ Waking up the server — free hosting naps when idle. This can take up to a minute."
  Hide it when requests succeed.
- **Retry while waking:** for GET requests, on network error or 502/503/504, retry up to 4 times
  with backoff (2 s, 5 s, 10 s, 20 s) before showing an error. Never auto-retry POST/PUT/DELETE.
- `429 RATE_LIMITED` and `507 STORAGE_FULL`: show the backend `message` in a toast (no generic error).
- Image picker uses `VITE_MAX_IMAGE_MB` for its client-side limit and hint text.
- "Use CineCircle with Claude" settings panel uses `VITE_MCP_URL` and shows the hosted
  Claude Code command with the `--header "Authorization: Bearer <token>"` form (see MCP below).
- `npm run build` and `npm run lint` must pass.

---

## MCP server

### Config
| Var | Local default | Production |
|---|---|---|
| `PORT` | 8090 | set by Render |
| `CINECIRCLE_API_URL` | `http://localhost:8080/api` | `https://<backend>.onrender.com/api` |
| `CINECIRCLE_WEB_URL` | `http://localhost:5173` | `https://<project>.pages.dev` (used for review links in tool output — replaces the hard-coded localhost link) |
| `MCP_ALLOW_ENV_TOKEN` | `true` | **`false`** |
| `MCP_RATE_LIMIT_PER_MINUTE` | 60 | 60 |

### Hosted mode: each caller uses their own token (replaces the single-token rule for HTTP)
- For HTTP requests, take the token from the **incoming** `Authorization: Bearer <jwt>` header of
  that MCP request and forward it to the backend for that call only. No header → read-only public
  access; write tools reply "Add your CineCircle token: see Settings → Use CineCircle with Claude".
- `CINECIRCLE_TOKEN` env is used only when `MCP_ALLOW_ENV_TOKEN=true` (local/stdio use).
- Use the mechanism your Spring AI version supports for reading transport/request headers inside
  a tool call (check its docs). **Tokens must never leak between callers or sessions** — no
  static/shared mutable state; prove it with a test where two concurrent clients with different
  tokens each act as themselves.
- Per-IP rate limit (`MCP_RATE_LIMIT_PER_MINUTE`) → a clear "slow down" tool error.
- Backend asleep: use a 70 s read timeout for backend calls and, on connect error / 502/503/504,
  retry once after 5 s; if still failing say "CineCircle is waking up — try again in a minute."
- `GET /health` → 200 (no auth). Never log tokens.

### Docker (`mcp-server/Dockerfile`, `.dockerignore`)
Same pattern and memory flags as the backend.

### README additions
Hosted URLs, and:
- Claude Code: `claude mcp add --transport http cinecircle https://<mcp>.onrender.com/<path> --header "Authorization: Bearer <your token>"`
- Read-only without a token: the same command without `--header`, or a custom connector URL in
  Claude apps if the user's plan supports custom connectors.
- Note: the first call after idle may take ~1–2 minutes (both services wake up).

### Tests
Header token forwarded per request; no header → read-only; env token ignored when
`MCP_ALLOW_ENV_TOKEN=false`; concurrent different tokens don't mix; rate limit; wake-up retry;
links use `CINECIRCLE_WEB_URL`; health endpoint.

---

## Seed data on Atlas
No code change: the v3 loader already loads into an **empty** database without reset and refuses
to reset remote databases. The human loads it once from their own machine (see DEPLOY-GUIDE.md).

## Repository hygiene (checked by deploy-reviewer)
- `.gitignore` covers `.idea/`, `.env`, `.env.*` (except `.env.example`), `target/`, `node_modules/`, `dist/`.
- No secrets anywhere in the repo **or its git history** (patterns: `sk-ant-`, `mongodb+srv://` with
  a password, a TMDB token, a JWT secret value).
