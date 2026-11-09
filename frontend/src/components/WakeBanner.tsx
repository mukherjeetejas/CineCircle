import { useServerWaking } from '../api/wake'

/**
 * Slim, non-blocking banner shown while any API request has been pending for 4 s+
 * (free hosting sleeps when idle; DEPLOY.md). Hides when the slow requests finish.
 */
export function WakeBanner() {
  const waking = useServerWaking()

  return (
    <div
      role="status"
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 top-16 z-50 flex justify-center px-3"
    >
      {waking && (
        <p className="pointer-events-auto max-w-[640px] rounded-full border border-amber/40 bg-surface-2/95 px-4 py-1.5 text-center text-xs text-ink shadow-lg backdrop-blur sm:text-sm">
          <span aria-hidden>☕ </span>
          Waking up the server — free hosting naps when idle. This can take up to a minute.
        </p>
      )}
    </div>
  )
}
