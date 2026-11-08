# CineCircle — Movie Reviews Social App

A Reddit-style social app where people post movie reviews, follow each other, upvote and comment.
Young, bold, amber-on-black look.

## Stack
- `backend/`: Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Security (JWT, stateless),
  Spring Data MongoDB, Bean Validation, GridFS for uploaded images, WebClient for TMDB. Port **8080**.
- `frontend/`: React 18 + Vite + TypeScript, react-router v6, TanStack Query, Tailwind CSS,
  lucide-react icons. Port **5173**. Calls `http://localhost:8080/api` (Vite proxy `/api` → 8080).
- Database: **local MongoDB** at `mongodb://localhost:27017/cinecircle` (MongoDB Community Server
  running as a Windows service, or `docker run -d -p 27017:27017 --name mongo mongo:7`).
- Movie catalogue: **TMDB API** (free for non-commercial use with attribution).

## Secrets / config
- `backend/src/main/resources/application.yml` reads env vars: `TMDB_API_TOKEN` (TMDB v4 read
  access token, sent as `Authorization: Bearer`), `JWT_SECRET` (≥32 chars), `MONGODB_URI`
  (default above). `TMDB_API_TOKEN` is optional at startup (`${TMDB_API_TOKEN:}`): without it the
  app boots with a warning, movie endpoints return 502 `TMDB_UNAVAILABLE`, and seeding skips posts.
- The TMDB token is **only ever used by the backend**. The frontend never calls TMDB directly
  except to load poster images from `https://image.tmdb.org/t/p/{size}{posterPath}`.
- Commit `.env.example`; never commit real secrets.

## Why TMDB (no garbage movie names)
A review can only be created for a movie that exists in TMDB. The client sends `tmdbId`; the
backend fetches/validates it from TMDB, caches it in the `movies` collection, and rejects unknown
IDs with 400 `MOVIE_NOT_FOUND`. Users never type a free-text movie title.
Footer/About page must show the TMDB logo and the line
"This product uses the TMDB API but is not endorsed or certified by TMDB."

## Domain model (MongoDB collections)
- `users`: id, username (unique, 3–20 chars `[a-z0-9_]`, lower-cased; reserved: `me`, `search`,
  `suggestions`), email (unique, lower-cased), passwordHash (BCrypt),
  displayName, bio (≤160), avatarImageId?, isPrivate (bool, default false), followerCount,
  followingCount, postCount, createdAt.
- `follows`: id, followerId, followeeId, status `ACCEPTED | PENDING`, createdAt.
  Unique compound index (followerId, followeeId). Following a private account creates PENDING.
- `movies` (TMDB cache): tmdbId (unique), title, originalTitle, releaseYear, overview, posterPath,
  backdropPath, genres[], tmdbVoteAverage, cachedAt. Refresh if older than 7 days.
- `posts` (a review): id, authorId, authorUsername, authorAvatarImageId (denormalised),
  authorIsPrivate (denormalised, update when user toggles privacy), tmdbId, movieTitle,
  movieYear (denormalised), title (≤120), body (1–5000, required, markdown-lite), rating (1–10 int),
  containsSpoilers (bool), imageType `NONE | TMDB_POSTER | CUSTOM`, imageRef (posterPath or GridFS id),
  upvoteCount, commentCount, hotScore, createdAt, updatedAt.
  Indexes: (authorId, createdAt desc), (tmdbId, createdAt desc), (tmdbId, upvoteCount desc),
  (authorIsPrivate, hotScore desc).
- `votes`: id, postId, userId, createdAt. Unique (postId, userId). Upvote only (no downvote).
- `comments`: id, postId, authorId, authorUsername, body (≤2000), parentCommentId? (one level of
  replies), deleted (soft delete shows "[deleted]"), createdAt.
- Images: GridFS bucket `images`. Accept jpeg/png/webp, max 5 MB.

## Visibility rules (enforce in backend services, never only in UI)
- A post is visible to a viewer if: author is the viewer, OR author is public, OR viewer has an
  ACCEPTED follow on the author.
- Private profile page shows header (name, avatar, counts, Follow/Requested button) but no posts
  unless the rule above passes.
- Popular feed and movie pages only include posts from public authors (plus the viewer's own, and
  private authors the viewer follows on movie pages).
- Only the author may delete their post or comment. Deleting a post deletes its votes and comments
  and its GridFS image, and decrements counters.

## Feed
- `GET /api/feed` (auth): posts from accounts the viewer follows (ACCEPTED) + own posts, newest
  first. If that page has < size items, backfill with popular public posts, de-duplicated, each
  item tagged `source: "FOLLOWING" | "POPULAR"`.
- Hot score (Reddit-like), recomputed on create and on each vote:
  `hotScore = log10(max(upvoteCount,1)) + createdAtEpochSeconds / 45000`.

## API contract (all JSON, prefix `/api`)
Paged responses: `{ "items": [...], "page": 0, "size": 20, "hasNext": true }` (page 0-based, size ≤ 50;
out-of-range values are clamped: size to 1–50, negative page to 0).
Errors: `{ "status": 400, "code": "VALIDATION_ERROR", "message": "...", "fieldErrors": { "field": "msg" } }`.
Error codes: VALIDATION_ERROR, UNAUTHORIZED, FORBIDDEN, NOT_OWNER, PRIVATE_ACCOUNT, NOT_FOUND,
MOVIE_NOT_FOUND, USERNAME_TAKEN, EMAIL_TAKEN, TMDB_UNAVAILABLE (502), METHOD_NOT_ALLOWED (405),
INTERNAL_ERROR (500). Create endpoints (`POST /auth/register`, `POST /posts`,
`POST /posts/{id}/comments`) return **201**; other successes 200 unless 204 is stated.
Auth header: `Authorization: Bearer <jwt>`. 🔓 = public (works without token), 🔒 = auth required.

### Auth
- 🔓 `POST /auth/register` `{username,email,password,displayName}` → `{token,user:UserProfile}`
  (password 8–100 chars)
- 🔓 `POST /auth/login` `{usernameOrEmail,password}` → `{token,user:UserProfile}`
- 🔒 `GET /users/me` → `UserProfile`

### Users & follows
- 🔓 `GET /users/{username}` → `UserProfile` (+ `followStatus: NONE|PENDING|ACCEPTED|SELF`, `canViewPosts`)
- 🔒 `PATCH /users/me` `{displayName?,bio?,isPrivate?}` → `UserProfile`; 🔒 `PUT /users/me/avatar` (multipart `file`) → `UserProfile`
- 🔓 `GET /users/{username}/posts?page&size` → paged `PostSummary` (403 `PRIVATE_ACCOUNT` if not allowed)
- 🔓 `GET /users/search?q=` → `UserSummary[]` (max 10)
- 🔓 `GET /users/suggestions` → `UserProfile[]` (max 5; public accounts, excluding the viewer and
  accounts the viewer already follows or has requested; ordered by followerCount desc)
- 🔒 `POST /users/{username}/follow` → `{followStatus}`; 🔒 `DELETE /users/{username}/follow` →
  `{followStatus:"NONE"}` (unfollows, or cancels a PENDING request; idempotent)
- 🔓 `GET /users/{username}/followers|following?page&size` → paged `UserSummary`
  (403 `PRIVATE_ACCOUNT` if the viewer may not see the account's posts)
- 🔒 `GET /users/me/follow-requests` → `UserSummary[]`
- 🔒 `POST /users/me/follow-requests/{username}/accept|reject` → 204

### Movies (TMDB-backed)
- 🔓 `GET /movies/search?q=&page=` → paged `MovieSummary` (proxied TMDB search; include `reviewCount` and `avgRating` from our posts if any)
- 🔓 `GET /movies/trending` → `MovieSummary[]` (TMDB trending/week, cached 1 h)
- 🔓 `GET /movies/{tmdbId}` → `MovieDetail` (+ `reviewCount`, `avgRating` from app users);
  unknown id → 404 `MOVIE_NOT_FOUND`
- `reviewCount` / `avgRating` (everywhere) count **only posts by public authors**.
- 🔓 `GET /movies/{tmdbId}/posts?sort=top|new&page&size` → paged `PostSummary` (visibility rules apply)

### Posts
- 🔒 `GET /feed?page&size` → paged `PostSummary` with `source`
- 🔓 `GET /posts/popular?window=day|week|all&page&size` → paged `PostSummary`
- 🔒 `POST /posts` multipart: part `post` (JSON `{tmdbId,title,body,rating,containsSpoilers,imageType}`) + optional part `image` (required when imageType=CUSTOM) → `PostDetail`. Multipart only (no plain-JSON variant).
- 🔓 `GET /posts/{id}` → `PostDetail` (404 if not visible)
- 🔒 `DELETE /posts/{id}` → 204 (403 `NOT_OWNER`)
- 🔒 `PUT /posts/{id}/upvote` → `{upvoted:true, upvoteCount}`; 🔒 `DELETE /posts/{id}/upvote` → `{upvoted:false, upvoteCount}` (both idempotent)

### Comments
- 🔓 `GET /posts/{id}/comments?page&size` → paged `Comment` (top-level, each with `replies[]`)
- 🔒 `POST /posts/{id}/comments` `{body,parentCommentId?}` → `Comment`
- 🔒 `DELETE /comments/{id}` → 204 (owner only, soft delete)

### Images
- 🔓 `GET /images/{id}` → image bytes with correct Content-Type and `Cache-Control: max-age=86400`

### DTO shapes
- `UserSummary`: `{id,username,displayName,avatarUrl,isPrivate}`
- `UserProfile`: UserSummary + `{bio,followerCount,followingCount,postCount,createdAt,followStatus,canViewPosts}`
- `MovieSummary`: `{tmdbId,title,releaseYear,posterUrl,tmdbVoteAverage,reviewCount,avgRating}`
- `MovieDetail`: MovieSummary + `{overview,backdropUrl,genres}`
- `PostSummary`: `{id,author:UserSummary,movie:{tmdbId,title,releaseYear,posterUrl},title,bodyPreview(≤300),rating,containsSpoilers,imageUrl,upvoteCount,commentCount,upvotedByMe,isMine,createdAt,source?}`
- `PostDetail`: PostSummary with full `body`
- `Comment`: `{id,author:UserSummary,body,deleted,createdAt,isMine,replies:Comment[]}`
- URLs are absolute: TMDB posters `https://image.tmdb.org/t/p/w500{path}`, backdrops
  `https://image.tmdb.org/t/p/w1280{path}`, our images `http://localhost:8080/api/images/{id}`.

## Design system (frontend)
- Colours: bg `#0A0A0A`, surface `#141414`, surface-2 `#1C1C1C`, border `#2A2A2A`,
  amber primary `#FFB000`, amber hover `#FFC233`, amber muted `#3D2A00`, text `#F5F5F5`,
  muted text `#9CA3AF`, danger `#EF4444`. Dark only.
- Fonts: "Space Grotesk" (headings, logo), "Inter" (body), from Google Fonts.
- Layout (Reddit-like): sticky top bar (logo, global search for movies + users, Create Post button,
  avatar menu); left rail (Home, Popular, My Profile, Follow requests badge); centre feed of post
  cards; right rail (Trending movies from TMDB, "Who to follow"). Collapses to single column +
  bottom nav under 768 px.
- Post card: left vertical upvote column (amber arrow when upvoted, count), movie chip (poster
  thumb + title + year, links to movie page), rating as amber stars/score badge, title, body
  preview, spoiler blur until clicked, image, footer actions (comments, share link, delete if mine).
- Vibe: rounded-2xl cards, subtle amber glow on hover, playful empty states, toasts, skeleton loaders.

## Ownership rules for agents
- `backend-dev` edits only `backend/`. `frontend-dev` edits only `frontend/`. `reviewer` edits nothing.
- The API contract above is the source of truth. If an agent needs to change it, it stops and
  reports the proposed change; the main session updates this file first.

## Run locally
1. Start MongoDB (service or docker command above).
2. `cd backend && mvn spring-boot:run` (env vars set) — or run `CineCircleApplication` in IntelliJ.
3. `cd frontend && npm install && npm run dev` → http://localhost:5173
4. Seed: on first start with an empty DB and `app.seed=true`, backend creates 5 demo users
   (password `password123`, one private), follows, ~20 posts on popular TMDB movies, votes and comments.
