/**
 * Setup strings for the "Use CineCircle with Claude" panel on /settings (FEATURES-V2.md, Feature 2;
 * hosted form per DEPLOY.md). Keep in sync with `mcp-server/README.md`.
 */
import { API_URL_ABSOLUTE, MCP_URL } from './env'

/** Streamable-HTTP endpoint of the CineCircle MCP server, from `VITE_MCP_URL` (default `http://localhost:8090/mcp`). */
export const MCP_HTTP_URL = MCP_URL

/** True when the MCP URL points at this machine (local dev) — then we also show how to start it. */
export const MCP_IS_LOCAL = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:|\/|$)/i.test(MCP_HTTP_URL)

/** Backend API base URL the MCP server talks to (Claude Desktop stdio config). */
export const MCP_CINECIRCLE_API_URL = API_URL_ABSOLUTE

/** Path of the built MCP jar (relative to the repo root; the user replaces `<path-to>`). */
export const MCP_JAR_PATH = '<path-to>/mcp-server/target/cinecircle-mcp-0.0.1-SNAPSHOT.jar'

/** Placeholders — the real token is never rendered on screen (copied via the token button). */
export const MCP_TOKEN_PLACEHOLDER = '<paste your token>'
export const MCP_HEADER_TOKEN_PLACEHOLDER = '<token>'

/** Command to start the MCP server locally in HTTP mode. */
export const MCP_RUN_COMMAND = 'cd mcp-server && mvn spring-boot:run'

/** Claude Code: register the HTTP server; each caller sends their own token in the header. */
export const CLAUDE_CODE_COMMAND = `claude mcp add --transport http cinecircle ${MCP_HTTP_URL} --header "Authorization: Bearer ${MCP_HEADER_TOKEN_PLACEHOLDER}"`

/** Claude Desktop: `claude_desktop_config.json` entry (stdio profile, launches the jar). */
export const CLAUDE_DESKTOP_CONFIG = {
  mcpServers: {
    cinecircle: {
      command: 'java',
      args: ['-jar', MCP_JAR_PATH, '--spring.profiles.active=stdio'],
      env: {
        CINECIRCLE_API_URL: MCP_CINECIRCLE_API_URL,
        CINECIRCLE_TOKEN: MCP_TOKEN_PLACEHOLDER,
      },
    },
  },
} as const

export const CLAUDE_DESKTOP_CONFIG_JSON = JSON.stringify(CLAUDE_DESKTOP_CONFIG, null, 2)
