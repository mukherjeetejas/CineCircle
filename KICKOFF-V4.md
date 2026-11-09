# CineCircle v4 — Make It Deployable

Code changes so CineCircle can run on free hosting with open sign-up and capped AI costs.
After this, follow `DEPLOY-GUIDE.md` to actually put it online.

New files only (nothing existing is changed):
- `DEPLOY.md` — what must change in the code (extends the earlier spec files)
- `DEPLOY-GUIDE.md` — step-by-step: Atlas → Render → Cloudflare → Claude
- `.claude/agents/deploy-reviewer.md` — new read-only reviewer
- `KICKOFF-V4.md` — this file

## Before you start
1. Everything works locally and is committed and pushed.
2. Copy these files into the project root and commit them:
   ```
   git add DEPLOY.md DEPLOY-GUIDE.md KICKOFF-V4.md .claude/agents/deploy-reviewer.md
   git commit -m "Add v4 deploy spec"
   ```
3. Start MongoDB. Run `claude` in the project root, `/agents` → 7 agents incl. `deploy-reviewer`.

## Prompt to paste into Claude Code
```
Make CineCircle deployable as specified in DEPLOY.md. It extends CLAUDE.md, FEATURES-V2.md and
SEED-DATA.md; where DEPLOY.md says it replaces a rule, DEPLOY.md wins. Every config key, env var,
error code, collection, endpoint and Dockerfile in DEPLOY.md is already approved by me — do not
stop to propose them. Do not add dependencies. Do not edit CLAUDE.md, FEATURES-V2.md,
SEED-DATA.md, DEPLOY.md, DEPLOY-GUIDE.md or anything in .claude/.

In every task you give a subagent, tell it to read CLAUDE.md, FEATURES-V2.md, SEED-DATA.md and
DEPLOY.md first, and which section is theirs. Local development must keep working with no env
changes.

Phase 1 — in parallel:
  backend-dev: the Backend section (config, health, cost protection, rate limits, storage guard,
    Dockerfile, tests).
  frontend-dev: the Frontend section (env vars, wake-up banner, GET retries, 429/507 messages,
    image limit, hosted MCP instructions).
  mcp-dev: the MCP server section (config, per-request token, rate limit, wake-up retry,
    Dockerfile, README, tests).
Phase 2 — deploy-reviewer: check everything against DEPLOY.md. Also run the existing reviewer
  and v2-reviewer once to confirm nothing older broke (ignore findings that only say something
  isn't in their spec file).
Phase 3 — send each BLOCKER/MAJOR to the right agent, re-run deploy-reviewer. Repeat until it
  says READY TO DEPLOY.

If the secret scan finds anything in git history, STOP and tell me before doing anything else.
If an agent needs anything not in the spec files, show it to me first.
Finish by listing every environment variable I must set on Render (backend and MCP) and on
Cloudflare Pages, with a one-line description each.
```

## After it says READY TO DEPLOY
```
git add -A
git commit -m "v4: deployable"
git push
```
Then open **DEPLOY-GUIDE.md** and follow it step by step.
