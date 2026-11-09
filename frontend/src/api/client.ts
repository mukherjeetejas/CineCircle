import toast from 'react-hot-toast'
import { getToken, setToken } from '../auth/token'
import { API_URL } from '../config/env'
import type { ApiErrorBody } from '../types'
import { trackRequest } from './wake'

/** Backend API base from `VITE_API_URL` (default `/api`, proxied to :8080 by Vite in dev). */
export const API_BASE = API_URL

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>
  /** True when apiFetch already showed a toast for this error (callers should not toast again). */
  toasted = false

  constructor(body: ApiErrorBody) {
    super(body.message || 'Something went wrong')
    this.name = 'ApiError'
    this.status = body.status
    this.code = body.code
    this.fieldErrors = body.fieldErrors ?? {}
  }
}

export interface ApiFetchOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** JSON body (serialised and sent with Content-Type: application/json). */
  json?: unknown
  /** Multipart body. Browser sets the boundary header. */
  form?: FormData
  /** Query params; undefined/null/'' values are dropped. */
  query?: Record<string, string | number | boolean | undefined | null>
  /**
   * Suppress the global error toast (caller handles the error in UI).
   * 429 RATE_LIMITED and 507 STORAGE_FULL are always toasted with the backend message.
   */
  silent?: boolean
  signal?: AbortSignal
}

/** Router-aware handler installed by AuthProvider; falls back to a hard redirect. */
let unauthorizedHandler: (() => void) | null = null
export function setUnauthorizedHandler(fn: (() => void) | null) {
  unauthorizedHandler = fn
}

/**
 * Free hosting sleeps when idle (DEPLOY.md): GETs that hit a network error or a 502/503/504 from
 * the hosting proxy are retried after these delays (4 retries, ~37 s total) before failing.
 * POST/PUT/PATCH/DELETE are never retried automatically.
 */
export const GET_RETRY_DELAYS_MS = [2_000, 5_000, 10_000, 20_000] as const
const RETRYABLE_STATUSES = new Set([502, 503, 504])

/** Error codes whose backend `message` is always shown in a toast, even for `silent` calls. */
const ALWAYS_TOAST_CODES = new Set(['RATE_LIMITED', 'STORAGE_FULL'])
const FALLBACK_MESSAGES: Record<number, string> = {
  429: 'Whoa, slow down! You have hit a limit. Try again later.',
  507: 'Image storage is full right now. Try posting without an upload.',
}

function buildUrl(path: string, query?: ApiFetchOptions['query']): string {
  const params = new URLSearchParams()
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== '') params.set(k, String(v))
    }
  }
  const qs = params.toString()
  return `${API_BASE}${path}${qs ? `?${qs}` : ''}`
}

async function readErrorBody(res: Response): Promise<Partial<ApiErrorBody>> {
  try {
    return (await res.json()) as Partial<ApiErrorBody>
  } catch {
    return {} // non-JSON error (proxy down, hosting platform HTML page...)
  }
}

function toApiError(res: Response, body: Partial<ApiErrorBody>): ApiError {
  return new ApiError({
    status: body.status ?? res.status,
    code: body.code ?? (res.status === 429 ? 'RATE_LIMITED' : res.status === 507 ? 'STORAGE_FULL' : `HTTP_${res.status}`),
    message:
      body.message ??
      FALLBACK_MESSAGES[res.status] ??
      (res.status >= 500 ? 'The server is having a moment. Try again shortly.' : res.statusText || 'Request failed'),
    fieldErrors: body.fieldErrors,
  })
}

function abortError(): DOMException {
  return new DOMException('The operation was aborted.', 'AbortError')
}

function isAbort(e: unknown): boolean {
  return e instanceof DOMException && e.name === 'AbortError'
}

/** setTimeout that rejects with AbortError as soon as `signal` aborts. */
function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(abortError())
      return
    }
    const onAbort = () => {
      window.clearTimeout(timer)
      reject(abortError())
    }
    const timer = window.setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

/**
 * The single fetch wrapper: attaches the Bearer token, parses the contract error shape,
 * retries GETs while the free-tier backend wakes up, clears the token and redirects to /login
 * on 401, and toasts other errors (unless `silent`).
 */
export async function apiFetch<T>(path: string, opts: ApiFetchOptions = {}): Promise<T> {
  const method = opts.method ?? 'GET'
  const canRetry = method === 'GET'
  const url = buildUrl(path, opts.query)

  let body: BodyInit | undefined
  const baseHeaders: Record<string, string> = { Accept: 'application/json' }
  if (opts.form) {
    body = opts.form
  } else if (opts.json !== undefined) {
    baseHeaders['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.json)
  }

  const done = trackRequest()
  try {
    for (let attempt = 0; ; attempt++) {
      const retriesLeft = canRetry && attempt < GET_RETRY_DELAYS_MS.length
      const headers = { ...baseHeaders }
      // Re-read per attempt: the user may have logged in/out while we were waiting.
      const token = getToken()
      if (token) headers.Authorization = `Bearer ${token}`

      let res: Response
      try {
        res = await fetch(url, { method, headers, body, signal: opts.signal })
      } catch (e) {
        if (isAbort(e)) throw e
        if (retriesLeft) {
          await sleep(GET_RETRY_DELAYS_MS[attempt], opts.signal)
          continue
        }
        const err = new ApiError({
          status: 0,
          code: 'NETWORK_ERROR',
          message: "Can't reach CineCircle right now. Check your connection and try again.",
        })
        if (!opts.silent) {
          toast.error(err.message, { id: 'network-error' })
          err.toasted = true
        }
        throw err
      }

      if (res.ok) {
        if (res.status === 204) return undefined as T
        const text = await res.text()
        return (text ? JSON.parse(text) : undefined) as T
      }

      const errBody = await readErrorBody(res)
      // A 502/503/504 carrying a CineCircle error code (TMDB_UNAVAILABLE, SUMMARY_UNAVAILABLE...) came
      // from our backend, which is therefore awake: retrying would only delay the error. A codeless one
      // comes from the hosting proxy while the service boots, so retry it.
      if (retriesLeft && RETRYABLE_STATUSES.has(res.status) && !errBody.code) {
        await sleep(GET_RETRY_DELAYS_MS[attempt], opts.signal)
        continue
      }

      const err = toApiError(res, errBody)
      const alwaysToast = ALWAYS_TOAST_CODES.has(err.code)
      if (res.status === 401 && token) {
        // Session expired / invalid token: clear it and send the user to log in.
        setToken(null)
        toast.error('Your session expired. Please log in again.', { id: 'session-expired' })
        err.toasted = true
        if (unauthorizedHandler) unauthorizedHandler()
        else if (!window.location.pathname.startsWith('/login')) window.location.assign('/login')
      } else if (!opts.silent || alwaysToast) {
        toast.error(err.message, { id: `${err.code}:${err.message}`, duration: alwaysToast ? 6000 : undefined })
        err.toasted = true
      }
      throw err
    }
  } finally {
    done()
  }
}

/** Multipart helper: a JSON part with Content-Type application/json, so Spring's @RequestPart can bind it. */
export function jsonBlob(value: unknown): Blob {
  return new Blob([JSON.stringify(value)], { type: 'application/json' })
}
