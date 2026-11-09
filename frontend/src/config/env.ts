/**
 * Deploy-time configuration (DEPLOY.md, Frontend). Every value comes from a Vite env var
 * (baked in at build time) with a local default, so `npm run dev` works with no .env file.
 * This is the ONLY place in the frontend that may contain a localhost URL.
 */

function str(value: string | undefined, fallback: string): string {
  const v = value?.trim()
  return v ? v : fallback
}

function stripTrailingSlash(url: string): string {
  return url.replace(/\/+$/, '')
}

/** Backend API base. Local default `/api` goes through the Vite proxy to :8080. */
export const API_URL = stripTrailingSlash(str(import.meta.env.VITE_API_URL, '/api'))

/** MCP server Streamable-HTTP endpoint (path `/mcp`, see mcp-server/README.md). */
export const MCP_URL = str(import.meta.env.VITE_MCP_URL, 'http://localhost:8090/mcp')

function parseMaxImageMb(raw: string | undefined): number {
  const n = Number(raw?.trim())
  return Number.isFinite(n) && n > 0 ? n : 5
}

/** Client-side image upload limit (the backend enforces its own `IMAGE_MAX_BYTES`). */
export const MAX_IMAGE_MB = parseMaxImageMb(import.meta.env.VITE_MAX_IMAGE_MB)
export const MAX_IMAGE_BYTES = Math.floor(MAX_IMAGE_MB * 1024 * 1024)

/** `/api` → `http://localhost:8080/api` style absolute URL, for text shown to users (e.g. MCP config). */
export const API_URL_ABSOLUTE = /^https?:\/\//i.test(API_URL) ? API_URL : 'http://localhost:8080/api'
