# CineCircle v2 — AI Review Summaries + MCP Server + AI Spoiler Detector

Adds three features without changing any v1 file. New files:
- `FEATURES-V2.md` — spec for v2 (extends CLAUDE.md)
- `.claude/agents/mcp-dev.md` — new agent that owns `mcp-server/`
- `.claude/agents/v2-reviewer.md` — new read-only reviewer for v2
- `KICKOFF-V2.md` — this file

The existing `backend-dev` and `frontend-dev` agents do their part of v2; the prompt below tells
them to read `FEATURES-V2.md`.

## One-time setup
1. Copy these files into the project root (next to `CLAUDE.md`). Nothing is overwritten.
2. Get an Anthropic API key at console.anthropic.com → API Keys, then (PowerShell, reopen terminal):
   ```powershell
   setx CINECIRCLE_LLM_API_KEY "sk-ant-..."
   ```
   Optional: `setx LLM_MODEL "claude-haiku-5-5"` (that's the default).
   Do NOT name it `ANTHROPIC_API_KEY` — Claude Code reads that variable and would switch from
   your Claude subscription to paid API billing.
3. Commit or back up your current working app first (`git add -A && git commit -m "v1"`), so you
   can diff or roll back v2.
4. Run `claude` in the project root and type `/agents` — you should see five agents:
   backend-dev, frontend-dev, reviewer, mcp-dev, v2-reviewer.

## Prompt to paste into Claude Code
```
Build CineCircle v2 as specified in FEATURES-V2.md. It extends CLAUDE.md; everything in
CLAUDE.md still applies. The endpoints, collection, env vars, error codes and dependencies listed
in FEATURES-V2.md are already approved by me — do not stop to propose them. Do not edit
CLAUDE.md, FEATURES-V2.md or any file in .claude/.

In every task you give a subagent, tell it to read CLAUDE.md and then FEATURES-V2.md first, and
which section is theirs.

Phase 1 — backend-dev: in backend/, build the shared LLM client, then the backend parts of
  Feature 1 (AI Review Summary) and Feature 3 (AI Spoiler Detector), with tests.
  Wait for it to finish so the summary endpoint and the new `spoiler` field exist.
Phase 2 — in parallel:
  frontend-dev: in frontend/, build the Feature 1 summary card, the Feature 3 spoiler tags /
    blur / "Show spoilers" button / create-form hint and toast, and the Feature 2
    "Use CineCircle with Claude" settings panel.
  mcp-dev: build Feature 2 (MCP server) in mcp-server/, including Feature 3 spoiler tags.
Phase 3 — v2-reviewer: check all v2 work against FEATURES-V2.md. Then also run the existing
  reviewer once to make sure v1 still passes (it only knows CLAUDE.md, so ignore
  any finding that just says the v2 endpoint/fields/mcp-server aren't in CLAUDE.md).
Phase 4 — send each BLOCKER/MAJOR issue to the right agent, then run v2-reviewer again.
  Repeat until v2-reviewer says SHIP.

If an agent needs anything not in CLAUDE.md or FEATURES-V2.md (new endpoint, field, dependency),
show it to me before doing it.
```

## Run v2
- Backend (8080) and frontend (5173) as before; open any movie page with 3+ public reviews to see
  the summary card.
- Write a review that gives away an ending without ticking "contains spoilers" — it should come
  back with a `🤖 AI: spoilers` tag, blurred, with a "Show spoilers" button. Reviews that existed
  before v2 are checked in the background (20 every 2 minutes).
- MCP server: `cd mcp-server && mvn spring-boot:run` (port 8090). See `mcp-server/README.md`.
- Connect Claude Code: `claude mcp add --transport http cinecircle <url from README>`, then ask
  "What do people on CineCircle think of Dune: Part Two?"
- To let Claude post as you: Settings → "Use CineCircle with Claude" → Copy my access token, set
  `CINECIRCLE_TOKEN` to it, then start the MCP server. Each account uses its own token.
