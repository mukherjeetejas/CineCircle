import { useSyncExternalStore } from 'react'

/**
 * Tracks in-flight API requests for the "waking up the server" banner (DEPLOY.md, free-hosting UX).
 * A request counts as slow once it has been pending (including its GET retries) for 4 s.
 * The banner is visible while at least one slow request is still pending, so it hides as soon
 * as the slow requests finish (in practice: succeed once the backend is awake).
 */

export const WAKE_BANNER_DELAY_MS = 4_000

let slowCount = 0
const listeners = new Set<() => void>()

function emit() {
  for (const l of listeners) l()
}

/** Call when a request starts; call the returned function exactly once when it settles. */
export function trackRequest(): () => void {
  let slow = false
  let done = false
  const timer = window.setTimeout(() => {
    if (done) return
    slow = true
    slowCount += 1
    emit()
  }, WAKE_BANNER_DELAY_MS)

  return () => {
    if (done) return
    done = true
    window.clearTimeout(timer)
    if (slow) {
      slowCount -= 1
      emit()
    }
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

const getSnapshot = () => slowCount > 0

export function useServerWaking(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot)
}
