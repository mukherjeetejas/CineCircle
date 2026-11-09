import { format, formatDistanceToNowStrict } from 'date-fns'
import { MAX_IMAGE_BYTES, MAX_IMAGE_MB } from '../config/env'

export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ')
}

export function timeAgo(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  if (diff < 45_000) return 'just now'
  return `${formatDistanceToNowStrict(d)} ago`
}

export function fullDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '' : format(d, 'PPpp')
}

export function joinedDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '' : format(d, 'MMMM yyyy')
}

export function compactNumber(n: number): string {
  return new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 }).format(n)
}

export function formatScore(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—'
  return (Math.round(n * 10) / 10).toFixed(1)
}

export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

/** "5 MB" / "1 MB" / "1.5 MB" — the configured upload limit (VITE_MAX_IMAGE_MB) for hint text. */
export const MAX_IMAGE_LABEL = `${Number.isInteger(MAX_IMAGE_MB) ? MAX_IMAGE_MB : MAX_IMAGE_MB.toFixed(1)} MB`

/** Returns an error message or null if the file is acceptable. */
export function validateImage(file: File): string | null {
  if (!(ACCEPTED_IMAGE_TYPES as readonly string[]).includes(file.type)) {
    return 'Only JPEG, PNG or WebP images are allowed.'
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return `That image is ${(file.size / 1024 / 1024).toFixed(1)} MB — the limit is ${MAX_IMAGE_LABEL}.`
  }
  return null
}

/** Only allow same-app relative return paths after login/register (no open redirects). */
export function safeFrom(state: unknown): string {
  const from = (state as { from?: unknown } | null)?.from
  return typeof from === 'string' &&
    from.startsWith('/') &&
    !from.startsWith('//') &&
    !from.startsWith('/login') &&
    !from.startsWith('/register')
    ? from
    : '/'
}

export function postUrl(id: string): string {
  return `${window.location.origin}/posts/${id}`
}
