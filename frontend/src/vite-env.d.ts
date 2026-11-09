/// <reference types="vite/client" />

/** Deploy-time settings (DEPLOY.md, Frontend). All optional: `src/config/env.ts` supplies the local defaults. */
interface ImportMetaEnv {
  /** Backend API base, e.g. `https://<backend>.onrender.com/api`. Default `/api` (Vite proxy). */
  readonly VITE_API_URL?: string
  /** CineCircle MCP server Streamable-HTTP endpoint. Default `http://localhost:8090/mcp`. */
  readonly VITE_MCP_URL?: string
  /** Client-side upload limit in MB. Default 5. */
  readonly VITE_MAX_IMAGE_MB?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
