import toast from 'react-hot-toast'
import { getToken, setToken } from '../auth/token'
import type { ApiErrorBody } from '../types'

export const API_BASE = '/api'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

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
  /** Suppress the global error toast (caller handles the error in UI). */
  silent?: boolean
  signal?: AbortSignal
}

/** Router-aware handler installed by AuthProvider; falls back to a hard redirect. */
let unauthorizedHandler: (() => void) | null = null
export function setUnauthorizedHandler(fn: (() => void) | null) {
  unauthorizedHandler = fn
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

async function parseError(res: Response): Promise<ApiError> {
  let body: Partial<ApiErrorBody> = {}
  try {
    body = (await res.json()) as Partial<ApiErrorBody>
  } catch {
    // non-JSON error (proxy down, HTML error page...)
  }
  return new ApiError({
    status: body.status ?? res.status,
    code: body.code ?? (res.status === 0 ? 'NETWORK_ERROR' : `HTTP_${res.status}`),
    message:
      body.message ??
      (res.status >= 500 ? 'The server is having a moment. Try again shortly.' : res.statusText || 'Request failed'),
    fieldErrors: body.fieldErrors,
  })
}

/**
 * The single fetch wrapper: attaches the Bearer token, parses the contract error shape,
 * clears the token and redirects to /login on 401, and toasts other errors (unless `silent`).
 */
export async function apiFetch<T>(path: string, opts: ApiFetchOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let body: BodyInit | undefined
  if (opts.form) {
    body = opts.form
  } else if (opts.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.json)
  }

  let res: Response
  try {
    res = await fetch(buildUrl(path, opts.query), {
      method: opts.method ?? 'GET',
      headers,
      body,
      signal: opts.signal,
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    const err = new ApiError({ status: 0, code: 'NETWORK_ERROR', message: "Can't reach CineCircle. Is the server running?" })
    if (!opts.silent) toast.error(err.message, { id: 'network-error' })
    throw err
  }

  if (!res.ok) {
    const err = await parseError(res)
    if (res.status === 401 && token) {
      // Session expired / invalid token: clear it and send the user to log in.
      setToken(null)
      toast.error('Your session expired. Please log in again.', { id: 'session-expired' })
      if (unauthorizedHandler) unauthorizedHandler()
      else if (!window.location.pathname.startsWith('/login')) window.location.assign('/login')
    } else if (!opts.silent) {
      toast.error(err.message, { id: `${err.code}:${err.message}` })
    }
    throw err
  }

  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** Multipart helper: a JSON part with Content-Type application/json, so Spring's @RequestPart can bind it. */
export function jsonBlob(value: unknown): Blob {
  return new Blob([JSON.stringify(value)], { type: 'application/json' })
}
