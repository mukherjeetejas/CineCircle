import { EyeOff } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '../lib/utils'

/**
 * Blurs its children while `hidden`. Controlled by the parent so one "Show spoilers / Hide" toggle can
 * reveal several regions of a post (title, body, image) at once. The blurred content keeps its size, so
 * revealing never shifts the layout.
 *
 * The overlay is a pointer convenience only (hidden from assistive tech and the tab order); the parent
 * renders the accessible toggle button.
 */
export function SpoilerGuard({
  hidden,
  onReveal,
  id,
  label = 'Spoilers — tap to reveal',
  children,
}: {
  hidden: boolean
  onReveal: () => void
  id?: string
  label?: string
  children: ReactNode
}) {
  return (
    <div className="relative" id={id}>
      <div
        className={cn(hidden && 'pointer-events-none select-none blur-md', 'transition-[filter] duration-300')}
        aria-hidden={hidden || undefined}
        // `inert` keeps links inside blurred content out of the tab order (React 18 needs the string form).
        {...(hidden ? { inert: '' } : {})}
      >
        {children}
      </div>
      {hidden && (
        <button
          type="button"
          tabIndex={-1}
          aria-hidden
          onClick={(e) => {
            e.preventDefault()
            e.stopPropagation()
            onReveal()
          }}
          className="absolute inset-0 flex items-center justify-center rounded-xl bg-black/30"
        >
          <span className="inline-flex items-center gap-2 rounded-full border border-amber/40 bg-bg/90 px-4 py-2 text-sm font-semibold text-amber shadow-glow-lg">
            <EyeOff className="h-4 w-4" aria-hidden />
            {label}
          </span>
        </button>
      )}
    </div>
  )
}
