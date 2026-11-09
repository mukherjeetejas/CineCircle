# CineCircle

A Reddit-style social app for movie reviews. Post reviews of real movies, follow people, upvote and comment, all in a bold amber-on-black UI.

Every review is tied to a real movie from [TMDB](https://www.themoviedb.org/), so there are no made-up or misspelled titles. Accounts can be public or private, and private accounts are only visible to followers they approve.

## Features

- **Reviews:** title, body, a 1–10 rating, a spoiler flag that blurs the text until clicked, and an optional image (the movie poster or your own upload).
- **Movie picker:** you can only review a movie by picking it from TMDB search results. The backend checks the movie ID with TMDB and rejects unknown IDs.
- **Feeds:**
  - **Home:** reviews from people you follow, topped up with popular reviews when that runs short.
  - **Popular:** Today / This week / All time, ranked by a Reddit-style hot score.
- **Movie pages:** backdrop, overview, genres, TMDB score next to the CineCircle score, and reviews sorted by Top or New.
- **Social:** follow and unfollow, private accounts with follow requests, upvotes, comments with one level of replies, "Who to follow" suggestions and trending movies.
- **Profiles:** avatar upload, bio, a private-account toggle, and lists of followers and following.
- **Privacy is enforced on the server.** Private reviews never appear in Popular, on movie pages or anywhere else for people who aren't approved followers.
- **Responsive layout:** three columns on desktop, one column with a bottom nav on phones.

## Tech stack

| Layer | Stack |
|---|---|
| Backend (`backend/`) | Java 21, Spring Boot 3.5, Maven, Spring Web, Spring Security (stateless JWT), Spring Data MongoDB, GridFS for images, WebClient for TMDB, Bean Validation |
| Frontend (`frontend/`) | React 18, Vite, TypeScript, react-router v6, TanStack Query, Tailwind CSS, lucide-react, react-hot-toast, date-fns |
| Database | MongoDB (local Community Server or Docker) |
| Movie data | TMDB API (free for non-commercial use with attribution) |

## Prerequisites

- **JDK 21** and **Maven 3.9+**, or IntelliJ IDEA, which bundles Maven
- **Node.js 20+** and npm
- **MongoDB** on `localhost:27017`: either MongoDB Community Server as a Windows service, or
  ```bash
  docker run -d -p 27017:27017 --name mongo mongo:7
  ```
- **A TMDB API Read Access Token** (free): themoviedb.org → Settings → API → *API Read Access Token*

## Configuration

The backend reads these environment variables. See [`backend/.env.example`](backend/.env.example) for a template.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `JWT_SECRET` | **yes** | | Secret for signing login tokens, at least 32 characters. The app won't start without it. |
| `TMDB_API_TOKEN` | recommended | *(empty)* | TMDB v4 read token. Without it the app still starts, but movie endpoints return `502 TMDB_UNAVAILABLE` and no demo reviews are seeded. |
| `MONGODB_URI` | no | `mongodb://localhost:27017/cinecircle` | MongoDB connection string, including the database name. |
| `APP_SEED` | no | `true` | Seed demo data on startup (see [Demo data](#demo-data)). |
| `APP_PUBLIC_BASE_URL` | no | `http://localhost:8080` | Public base URL of the backend, used to build absolute image URLs. |
| `CINECIRCLE_LLM_API_KEY` | no | *(empty)* | Anthropic API key for AI summaries and spoiler checks. Without it AI features are disabled. |
| `PORT` | no | `8080` | HTTP port (set automatically by Render). |
| `APP_CORS_ORIGINS` | no | `http://localhost:5173` | Allowed frontend origin(s), comma-separated. |
| `IMAGE_MAX_BYTES` | no | `5242880` | Maximum upload size per image (production: `1048576`). |
| `IMAGE_STORAGE_LIMIT_MB` | no | `300` | Total image storage cap; uploads beyond it get `507 STORAGE_FULL`. |
| `LLM_DAILY_CALL_LIMIT` | no | `150` | Maximum AI calls per UTC day. |
| `LIMIT_POSTS_PER_DAY` | no | `10` | Reviews per user per day (`429 RATE_LIMITED` beyond). |
| `LIMIT_COMMENTS_PER_DAY` | no | `50` | Comments per user per day. |
| `LIMIT_REGISTER_PER_HOUR` | no | `5` | Sign-ups per IP per hour. |
| `LIMIT_LOGIN_PER_MINUTE` | no | `10` | Login attempts per IP per minute. |

For hosting (Render, Cloudflare Pages, Atlas), see [`DEPLOY-GUIDE.md`](DEPLOY-GUIDE.md).

To set them permanently on Windows (open a new terminal afterwards):

```powershell
setx JWT_SECRET "change-me-to-a-long-random-string-at-least-32-chars"
setx TMDB_API_TOKEN "your_tmdb_read_access_token"
```

> Only the backend ever uses the TMDB token. Never commit real secrets; only `.env.example` belongs in git.

## Running locally

**1. Start MongoDB.** Either start the Windows service or use the Docker command above.

**2. Start the backend** on http://localhost:8080:

```bash
cd backend
mvn spring-boot:run
```

You can also run `com.cinecircle.CineCircleApplication` from IntelliJ, with the environment variables added to its run configuration.

**3. Start the frontend** on http://localhost:5173:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server forwards `/api` requests to `localhost:8080`, so open **http://localhost:5173**.

### Demo data

When `APP_SEED=true`, the backend seeds the database on startup:

- **Users and follows:** created when there are no users yet. That includes 5 demo users and their follows, one of which is a pending request.
- **Reviews, votes and comments:** about 20 reviews of trending TMDB movies, plus their votes and comments. These are created whenever there are no reviews and TMDB can be reached. If TMDB is unavailable, the seeder tries again on the next start.

All demo users have the password `password123`:

| Username | Notes |
|---|---|
| `alice` | Horror fan, follows bob and carol |
| `bob` | Blockbuster fan |
| `carol` | **Private account**: only approved followers see her reviews |
| `dave` | Sci-fi fan, has a pending follow request to carol |
| `erin` | Nolan fan |

Set `APP_SEED=false` to turn seeding off. This also stops the demo reviews from being recreated if every review is deleted.

## Testing

```bash
# Backend: unit tests plus MockMvc integration tests (embedded MongoDB, mocked TMDB)
cd backend
mvn test

# Frontend: type-check, production build and lint
cd frontend
npm run build
npm run lint
```

The first backend test run downloads an embedded MongoDB binary, which takes a couple of minutes. Later runs take about 10 seconds.

## Project structure

```
.
├── backend/                     Spring Boot API (port 8080)
│   ├── src/main/java/com/cinecircle/
│   │   ├── auth/                register / login
│   │   ├── user/  follow/       profiles, privacy, follow requests
│   │   ├── movie/  movie/tmdb/  TMDB client, movie cache, search, trending
│   │   ├── post/  feed/         reviews, popular, hot score, home feed
│   │   ├── vote/  comment/      upvotes, threaded comments
│   │   ├── image/               GridFS upload and serving
│   │   ├── security/  config/   JWT filter, CORS, security config
│   │   ├── common/              error handling, paging
│   │   └── seed/                demo data
│   └── src/main/resources/application.yml
├── frontend/                    React app (port 5173)
│   └── src/
│       ├── api/                 typed API client + TanStack Query hooks
│       ├── auth/                auth context, token storage, route guard
│       ├── components/          post card, movie picker, upvote, ...
│       ├── layout/              top bar, left/right rails, bottom nav
│       ├── lib/                 shared hooks and helpers
│       ├── pages/               one file per route
│       └── types.ts             API DTO types
├── CLAUDE.md                    full spec: data model, visibility rules, API contract
└── README.md
```

## API overview

All endpoints are under `/api` and return JSON. Send the token as `Authorization: Bearer <jwt>`; public endpoints also read it when present. The complete contract (every endpoint, DTO shape and error code) is in [`CLAUDE.md`](CLAUDE.md).

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `GET /users/me` |
| Users & follows | `GET /users/{username}` (+ `/posts`, `/followers`, `/following`), `PATCH /users/me`, `PUT /users/me/avatar`, `GET /users/search`, `GET /users/suggestions`, `POST`/`DELETE /users/{username}/follow`, `GET /users/me/follow-requests` (+ `/accept`, `/reject`) |
| Movies | `GET /movies/search`, `GET /movies/trending`, `GET /movies/{tmdbId}`, `GET /movies/{tmdbId}/posts` |
| Posts | `GET /feed`, `GET /posts/popular`, `POST /posts` (multipart), `GET`/`DELETE /posts/{id}`, `PUT`/`DELETE /posts/{id}/upvote` |
| Comments | `GET`/`POST /posts/{id}/comments`, `DELETE /comments/{id}` |
| Images | `GET /images/{id}` |

Paged responses look like `{ "items": [...], "page": 0, "size": 20, "hasNext": true }`. Errors look like `{ "status", "code", "message", "fieldErrors" }`.

## Troubleshooting

- **The backend won't start and says `JWT_SECRET` is missing.** Set it (at least 32 characters) and open a new terminal; `setx` doesn't affect terminals that are already open.
- **Movie search, trending or creating a review returns 502 `TMDB_UNAVAILABLE`.** Check that `TMDB_API_TOKEN` is set. The client retries brief network drops, but a network that blocks TMDB will still fail.
- **There are no demo reviews.** TMDB was unreachable when the seeder ran. Restart the backend once TMDB is reachable; it seeds reviews whenever there are none.
- **You're using a database other than `cinecircle`.** Set `MONGODB_URI`, for example `mongodb://localhost:27017/movieApp`.
- **Port 8080 or 5173 is already in use.** Stop the other process. The frontend expects the API on 8080.

## Attribution

<img src="https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg" alt="TMDB logo" height="16">

This product uses the TMDB API but is not endorsed or certified by TMDB.
