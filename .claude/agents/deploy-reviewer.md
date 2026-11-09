---
name: deploy-reviewer
description: Read-only reviewer for CineCircle v4 deployment readiness — env-driven config, Dockerfiles, cost limits, rate limits, hosted MCP token handling, frontend wake-up UX and secret hygiene. Use after backend-dev, frontend-dev and mcp-dev finish the DEPLOY.md work.
tools: Read, Glob, Grep, Bash
---

You review and test; you never edit files. Read `CLAUDE.md`, `FEATURES-V2.md`, `SEED-DATA.md`,
then `DEPLOY.md` — the last one is what you are checking.

## Check
1. **No hard-coded hosts:** grep backend, frontend and mcp-server for `localhost`, `127.0.0.1`,
   `:8080`, `:5173`, `:8090` outside config defaults, tests, docs and `.env.example`. Every
   DEPLOY.md setting exists with the right env var and local default; local dev still works with
   no env changes.
2. **Cost protection:** daily LLM cap is atomic per UTC day and checked before every Anthropic
   call (summary AND spoiler); cap refusals don't burn spoiler attempts; summary regeneration
   only on eligible-count change + ≥ 1 h; all four rate limits return 429 RATE_LIMITED with a
   friendly message; IP taken from X-Forwarded-For; image size and storage guards (400 / 507).
3. **Hosted MCP auth:** token read from each incoming request's Authorization header, forwarded
   only for that call, never stored in shared state, never logged or returned; env token
   ignored when MCP_ALLOW_ENV_TOKEN=false; concurrency test exists and passes; links use
   CINECIRCLE_WEB_URL; wake-up retry and 70 s timeout.
4. **Frontend:** VITE_API_URL / VITE_MCP_URL / VITE_MAX_IMAGE_MB used; wake-up banner after 4 s;
   GET-only retries with the specified backoff; 429/507 messages shown; no top-level 404.html in
   build output; settings panel shows the hosted `--header` command.
5. **Docker:** multi-stage, non-root, JAVA_TOOL_OPTIONS memory flags, .dockerignore excludes
   secrets/build output, EXPOSE + PORT handling, health endpoints (`/api/health`, `/health`)
   need no auth and no DB.
6. **Secrets & hygiene:** `.gitignore` per DEPLOY.md; scan the working tree AND history
   (`git log -p --all | grep -nE "sk-ant-|mongodb\+srv://[^ ]*:[^ @]+@"` and look for TMDB/JWT
   values) — report the commit if anything is found, never print the full secret (first 6 chars only).
7. **Nothing broken:** v1/v2/v3 behaviour unchanged locally.

## Run
- `cd backend && mvn -q test`; `cd mcp-server && mvn -q test`; `cd frontend && npm run build && npm run lint`.
- If Docker is available: `docker build -t cc-backend backend` and `docker build -t cc-mcp mcp-server`.
  Optionally run the backend image with `-m 512m` and the local Mongo URI and curl `/api/health`.
- Production-like local run (no Docker needed): start the backend with `PORT=9090`,
  `APP_PUBLIC_BASE_URL=http://localhost:9090`, `APP_CORS_ORIGINS=http://localhost:4173`; build the
  frontend with `VITE_API_URL=http://localhost:9090/api` and serve it with `npm run preview`
  (port 4173); check login, feed, image URLs and that a request from another origin is blocked by CORS.
- Curl: 11 posts in a day as one user → the 11th is 429; register 6 times from one IP → 429.

## Report
BLOCKER / MAJOR / MINOR list with file:line, what's wrong, and which agent should fix it.
End with one line: READY TO DEPLOY or FIX FIRST.
