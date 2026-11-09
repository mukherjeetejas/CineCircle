# CineCircle — Deployment Guide (free, step by step)

Do this **after** `KICKOFF-V4.md` says READY TO DEPLOY and you have pushed to GitHub.

| Part | Service | Cost |
|---|---|---|
| Database | MongoDB Atlas — Free cluster | $0 |
| Backend (8080) + MCP server (8090) | Render — 2 Free web services (Docker) | $0 (sleep after 15 min idle) |
| Frontend | Cloudflare Pages | $0 |
| AI | Anthropic (your credits, capped) | cents |

Keep a notes file open (NOT in the repo) to paste URLs and secrets as you go:
```
ATLAS_URI=
JWT_SECRET=
TMDB_API_TOKEN=
CINECIRCLE_LLM_API_KEY=
BACKEND_URL=      (https://....onrender.com)
FRONTEND_URL=     (https://....pages.dev)
MCP_URL=          (https://....onrender.com)
```
Order matters because each service needs the previous one's URL.

---

## Step 0 — Before anything (5 min)
- [ ] Anthropic: workspace `cinecircle`, org spend limit (e.g. $3) + email alert, workspace limit
      ($2), auto-reload OFF. The app's key belongs to that workspace.
- [ ] Generate a production JWT secret (different from your local one). In a terminal:
      ```
      node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
      ```
      Paste it as `JWT_SECRET` in your notes.
- [ ] `git status` is clean and `git push` is done.

---

## Step 1 — Database: MongoDB Atlas (10 min)
1. Sign up at **mongodb.com/cloud/atlas** (Google login is fine). No card needed for the free cluster.
2. Create a project `cinecircle` → **Create cluster** → choose **Free**.
   Provider **AWS**, region **Singapore (ap-southeast-1)** — same region as Render below, so the
   backend ↔ database hop is fast. Name: `cinecircle`. Create.
3. **Database user:** Security → Database Access → Add user → Password auth.
   Username `cinecircle-app`, click **Autogenerate** password (letters/numbers only — easier),
   copy it to your notes. Role: **Read and write to any database** (or restrict to `cinecircle`). Add.
4. **Network access:** Security → Network Access → Add IP → **Allow access from anywhere**
   (`0.0.0.0/0`). Render's free services have no fixed IP, so this is needed; your strong
   generated password is what protects the database.
5. **Connection string:** Clusters → **Connect** → Drivers → copy the `mongodb+srv://...` string.
   Put your password in place of `<db_password>` and add the database name before the `?`:
   ```
   mongodb+srv://cinecircle-app:PASSWORD@cinecircle.xxxxx.mongodb.net/cinecircle?retryWrites=true&w=majority
   ```
   Save as `ATLAS_URI` in your notes.

### 1b — Load the showcase data into Atlas (from your PC, once)
In a **new** PowerShell window, from the `backend/` folder (your other env vars are already set in Windows):
```powershell
$env:MONGODB_URI="<ATLAS_URI>"
$env:APP_SEED="false"
$env:SHOWCASE_RESET="false"
mvn spring-boot:run "-Dspring-boot.run.profiles=showcase"
```
Wait for the loader report (loaded counts), then **Ctrl+C** and **close this window** —
otherwise your local app would keep writing to the live database.
Check in Atlas → Browse Collections: `users` has 25 documents, `posts` 270.
(Reset is deliberately impossible on Atlas; if you need to reload, drop the database in Atlas first.)

---

## Step 2 — Backend on Render (15 min + build time)
1. Sign up at **render.com** with **GitHub**. Allow access to your CineCircle repo.
   (Render may ask for card verification; Free instances are not charged.)
2. **New → Web Service** → pick your repo.
3. Settings:
   - Name: `cinecircle-backend` (your URL becomes `https://cinecircle-backend.onrender.com` if
     the name is free; otherwise Render adds a suffix — use whatever it shows)
   - Region: **Singapore** · Branch: `main`
   - **Root Directory: `backend`** · Runtime/Language: **Docker**
     (Dockerfile path `./Dockerfile`, context `.` — relative to the root directory)
   - Instance type: **Free**
4. **Environment variables** (Add from .env or one by one):
   | Key | Value |
   |---|---|
   | `MONGODB_URI` | your `ATLAS_URI` |
   | `JWT_SECRET` | your new production secret |
   | `TMDB_API_TOKEN` | your TMDB read access token |
   | `CINECIRCLE_LLM_API_KEY` | your `sk-ant-...` key |
   | `APP_SEED` | `false` |
   | `APP_PUBLIC_BASE_URL` | `https://cinecircle-backend.onrender.com` (fix after you see the real URL) |
   | `APP_CORS_ORIGINS` | `http://localhost:5173` (temporary — replaced in Step 3) |
   | `IMAGE_MAX_BYTES` | `1048576` |
   | `LLM_DAILY_CALL_LIMIT` | `150` |
   Plus any other variable the v4 kickoff listed at the end.
5. **Advanced → Health Check Path:** `/api/health`. Auto-Deploy: **On**.
6. **Create Web Service.** The first Docker build takes ~5–10 minutes; watch the Logs.
7. Test in your browser:
   - `https://<BACKEND_URL>/api/health` → `{"status":"ok"}`
   - `https://<BACKEND_URL>/api/movies/trending` → a list of movies
   Save the real URL as `BACKEND_URL`. If it differs from what you typed in `APP_PUBLIC_BASE_URL`,
   fix that variable (Environment tab → Save → it redeploys).

---

## Step 3 — Frontend on Cloudflare Pages (10 min)
1. Sign up at **dash.cloudflare.com** (no card needed).
2. **Workers & Pages → Create** → choose the **Pages** tab → **Connect to Git** → authorise GitHub →
   pick the repo. (If the screen pushes you to "Workers", look for the Pages option / "Import an
   existing Git repository" under Pages.)
3. Build settings:
   - Project name: `cinecircle` (URL becomes `https://cinecircle.pages.dev` if free)
   - Production branch: `main` · Framework preset: **None** (or Vite)
   - **Root directory: `frontend`** · Build command: `npm run build` · Output directory: `dist`
4. **Environment variables (Production):**
   | Key | Value |
   |---|---|
   | `VITE_API_URL` | `https://<BACKEND_URL>/api` |
   | `VITE_MAX_IMAGE_MB` | `1` |
   | `NODE_VERSION` | `20` (or the version you use locally — `node -v`) |
   (`VITE_MCP_URL` is added in Step 5.)
5. **Save and Deploy.** When done, save the URL as `FRONTEND_URL`.
6. **Back on Render → cinecircle-backend → Environment:** set
   `APP_CORS_ORIGINS` = `https://<FRONTEND_URL>` (exact, no trailing slash) → Save (redeploys, ~2–5 min).
7. Open `FRONTEND_URL`. First load may show "☕ Waking up the server…" for up to a minute.
   Log in as **demo / password123**.

---

## Step 4 — MCP server on Render (10 min + build)
1. Render → **New → Web Service** → same repo.
   Name `cinecircle-mcp` · Region **Singapore** · **Root Directory `mcp-server`** · **Docker** · **Free**.
2. Environment variables:
   | Key | Value |
   |---|---|
   | `CINECIRCLE_API_URL` | `https://<BACKEND_URL>/api` |
   | `CINECIRCLE_WEB_URL` | `https://<FRONTEND_URL>` |
   | `MCP_ALLOW_ENV_TOKEN` | `false` |
3. Advanced → Health Check Path: `/health` → Create.
4. When live, save `MCP_URL`. The full MCP endpoint is `MCP_URL` + the path in `mcp-server/README.md`.

## Step 5 — Connect the pieces
1. Cloudflare Pages → your project → Settings → Environment variables → add
   `VITE_MCP_URL` = full MCP endpoint → then **Deployments → Retry deployment** (Vite bakes env
   vars in at build time, so a rebuild is required after changing any `VITE_` variable).
2. In the live site: **Settings → Use CineCircle with Claude → Copy my access token**.
3. Wake both services (open the site, then open `MCP_URL/health`), then in a terminal:
   ```
   claude mcp add --transport http cinecircle <full MCP endpoint> --header "Authorization: Bearer <token>"
   ```
   Start `claude` and ask: *"What's trending on CineCircle, and which of those movies have the best reviews?"*
   The token expires after 7 days — copy a new one and run `claude mcp remove cinecircle` then add again.

---

## Step 6 — Smoke test (10 min)
- [ ] Logged out: Popular page loads, a movie page shows a summary card (may take a few seconds
      the first time — it's generated once and cached).
- [ ] Refresh the browser on a deep link like `/movies/<id>` → page loads (not a 404).
- [ ] Register a new account → write a review with an unmarked spoiler → it comes back blurred
      with the 🤖 tag.
- [ ] Upload a 2 MB image → friendly "under 1 MB" message.
- [ ] Private profile as a stranger → lock card.
- [ ] Claude via MCP: read reviews, then post a review as yourself.
- [ ] Anthropic Console → Usage: a few cents at most.

After loading, the background job spoiler-checks the 270 seeded reviews (20 every 2 minutes,
only while the backend is awake) — browse the site for a while or just let it catch up over a few visits.

---

## Keeping it free and safe
- **Never** upgrade a Render service or the Atlas cluster unless you decide to pay.
- Watch: Anthropic Usage (weekly), Atlas → cluster metrics (storage stays far below 512 MB),
  Render → service Logs for errors.
- Updating the app = `git push` to `main`. Render and Cloudflare rebuild automatically
  (Render only rebuilds a service when files in its root directory change).
- If you ever leak a secret: rotate it immediately (new Anthropic key / new Atlas password /
  new JWT secret), update it on Render, and the old one stops working.

## Troubleshooting
| Symptom | Likely cause → fix |
|---|---|
| Browser console says **CORS** blocked | `APP_CORS_ORIGINS` doesn't exactly match the Pages URL (https, no trailing slash) |
| Site works but every call goes to `/api` on pages.dev | `VITE_API_URL` missing → add it and **retry the deployment** |
| Refresh on a page gives **404** | Add `frontend/public/_redirects` with `/* /index.html 200`, push |
| Render log: **OutOfMemoryError** / instance restarts | Ask Claude Code to lower `MaxRAMPercentage` to 60 in the Dockerfile |
| Render log: Mongo **timeout** | Atlas Network Access missing `0.0.0.0/0`, or wrong password in `MONGODB_URI` |
| Images broken | `APP_PUBLIC_BASE_URL` wrong (must be the backend's https URL) |
| Summaries never appear | Movie has < 3 eligible reviews, AI key missing on Render, or daily cap reached (resets 00:00 UTC = 05:30 IST) |
| Claude says CineCircle is waking up | Normal after idle — wait a minute and ask again |
| 401 errors in Claude | Token expired (7 days) → copy a new one, re-add the MCP server |
