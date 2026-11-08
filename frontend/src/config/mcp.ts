/**
 * Setup strings for the "Use CineCircle with Claude" panel on /settings (FEATURES-V2.md, Feature 2).
 *
 * SINGLE SOURCE OF TRUTH in the frontend — keep these in sync with `mcp-server/README.md`.
 * If the README ends up with a different URL / jar name / profile, change them here only.
 */

/** Streamable-HTTP endpoint of the CineCircle MCP server (for Claude Code / remote clients). */
export const MCP_HTTP_URL = 'http://localhost:8090/mcp'

/** Backend API base URL the MCP server talks to. */
export const MCP_CINECIRCLE_API_URL = 'http://localhost:8080/api'

/** Path of the built MCP jar (relative to the repo root; the user replaces `<path-to>`). */
export const MCP_JAR_PATH = '<path-to>/mcp-server/target/cinecircle-mcp-0.0.1-SNAPSHOT.jar'

/** Placeholder shown in the Desktop config — the real token is never rendered on screen. */
export const MCP_TOKEN_PLACEHOLDER = '<paste your token>'

/** Command to start the MCP server in HTTP mode. */
export const MCP_RUN_COMMAND = 'cd mcp-server && mvn spring-boot:run'

/** Claude Code: one-liner to register the HTTP server. */
export const CLAUDE_CODE_COMMAND = `claude mcp add --transport http cinecircle ${MCP_HTTP_URL}`

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
