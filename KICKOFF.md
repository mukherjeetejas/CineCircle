# How to run the build with Claude Code

## One-time setup
1. Install MongoDB Community Server (runs as a Windows service on 27017) or use Docker:
   `docker run -d -p 27017:27017 --name mongo mongo:7`
2. Get a free TMDB key: themoviedb.org → Settings → API → copy the **API Read Access Token**.
3. Set env vars (PowerShell, then reopen the terminal):
   ```powershell
   setx TMDB_API_TOKEN "your_tmdb_read_access_token"
   setx JWT_SECRET "change-me-to-a-long-random-string-at-least-32-chars"
   ```
4. Put this folder (CLAUDE.md + .claude/agents/) where you want the project, open it in IntelliJ,
   open the terminal (Alt+F12) and run `claude`. Type `/agents` to confirm the three agents load.

## Prompt to paste into Claude Code
```
Build CineCircle exactly as specified in CLAUDE.md.

Phase 1 — Use the backend-dev and frontend-dev subagents in parallel.
  backend-dev: create backend/ with all endpoints, Mongo indexes, TMDB client, GridFS images,
  JWT auth, seed data and tests.
  frontend-dev: create frontend/ with all pages, the create-review flow, and the amber/black
  Reddit-style UI, coding against the contract in CLAUDE.md (mock nothing, call the real API).
Phase 2 — Use the reviewer subagent to check both against CLAUDE.md.
Phase 3 — Send each BLOCKER/MAJOR issue back to the right agent, then run reviewer again.
Repeat until the reviewer says SHIP.

If an agent proposes an API contract change, show it to me before updating CLAUDE.md.
Ask me before adding any dependency not listed in CLAUDE.md or the agent files.
```

## Run the app
- Backend: run `CineCircleApplication` in IntelliJ (or `cd backend && mvn spring-boot:run`)
- Frontend: `cd frontend && npm install && npm run dev` → http://localhost:5173
- Demo login: any seeded user, password `password123`
