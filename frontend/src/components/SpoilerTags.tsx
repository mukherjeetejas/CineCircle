import { AlertTriangle, Bot, Eye, EyeOff, ScanSearch } from 'lucide-react'
import type { SpoilerView } from '../lib/spoiler'
import { cn } from '../lib/utils'

const pill = 'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-semibold tracking-wide'

/** Tag pills shown next to the rating: author flag, AI flag (with reason) and "checking" state. */
export function SpoilerTags({ view }: { view: SpoilerView }) {
  if (!view.blurred) return null
  return (
    <>
      {view.byAuthor && (
        <span className={cn(pill, 'border-danger/40 text-danger')} title="The author marked this review as containing spoilers">
          <AlertTriangle className="h-3 w-3" aria-hidden />
          Spoilers
        </span>
      )}
      {view.byAi && (
        <span
          className={cn(pill, 'max-w-full border-amber/40 bg-amber-muted/40 text-amber')}
          title={view.aiReason ? `Flagged by our AI: ${view.aiReason}` : 'Flagged by our AI as containing spoilers'}
        >
          <Bot className="h-3 w-3 shrink-0" aria-hidden />
          <span className="shrink-0">AI: spoilers</span>
          {view.aiReason && (
            <span className="truncate font-normal text-amber/80">
              <span aria-hidden> · </span>
              <span className="sr-only">, reason: </span>
              {view.aiReason}
            </span>
          )}
        </span>
      )}
      {view.pending && (
        <span className={cn(pill, 'border-line bg-surface-2 text-muted')} role="status">
          <ScanSearch className="h-3 w-3 animate-pulse" aria-hidden />
          Checking for spoilers…
        </span>
      )}
    </>
  )
}

/**
 * The accessible reveal toggle. Fixed min-width + same height in both states so swapping the
 * label never nudges neighbouring content.
 */
export function SpoilerToggle({
  revealed,
  onToggle,
  controls,
}: {
  revealed: boolean
  onToggle: () => void
  /** Space-separated ids of the regions this toggle reveals. */
  controls: string
}) {
  // Deliberately no post title in the label: it may itself be a spoiler (titleHidden).
  return (
    <button
      type="button"
      onClick={(e) => {
        e.preventDefault()
        e.stopPropagation()
        onToggle()
      }}
      aria-expanded={revealed}
      aria-controls={controls}
      aria-label={revealed ? 'Hide spoilers in this review' : 'Show spoilers in this review'}
      className={cn(
        'inline-flex min-w-[8.5rem] items-center justify-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-semibold transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber focus-visible:ring-offset-2 focus-visible:ring-offset-bg',
        revealed
          ? 'border-line text-muted hover:border-amber hover:text-amber'
          : 'border-amber/60 bg-amber text-black hover:bg-amber-hover',
      )}
    >
      {revealed ? <EyeOff className="h-3 w-3" aria-hidden /> : <Eye className="h-3 w-3" aria-hidden />}
      {revealed ? 'Hide' : 'Show spoilers'}
    </button>
  )
}
