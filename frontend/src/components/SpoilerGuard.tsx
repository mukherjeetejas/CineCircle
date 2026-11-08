import { EyeOff } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { cn } from '../lib/utils'

/** Blurs its children until the reader explicitly reveals them. */
export function SpoilerGuard({ active, children }: { active: boolean; children: ReactNode }) {
  const [revealed, setRevealed] = useState(false)
  const hidden = active && !revealed

  return (
    <div className="relative">
      <div
        className={cn(hidden && 'pointer-events-none select-none blur-md', 'transition-[filter] duration-300')}
        aria-hidden={hidden || undefined}
      >
        {children}
      </div>
      {hidden && (
        <button
          type="button"
          onClick={(e) => {
            e.preventDefault()
            e.stopPropagation()
            setRevealed(true)
          }}
          className="absolute inset-0 flex items-center justify-center rounded-xl bg-black/30 focus-visible:ring-2 focus-visible:ring-amber"
        >
          <span className="inline-flex items-center gap-2 rounded-full border border-amber/40 bg-bg/90 px-4 py-2 text-sm font-semibold text-amber shadow-glow-lg">
            <EyeOff className="h-4 w-4" aria-hidden />
            Spoilers — tap to reveal
          </span>
        </button>
      )}
    </div>
  )
}
