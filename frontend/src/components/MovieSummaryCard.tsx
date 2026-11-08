import { AlertCircle, Minus, PenLine, Plus, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useMovieSummary } from '../api/movies'
import { cn, formatScore, fullDate, timeAgo } from '../lib/utils'
import type { SummarySentiment } from '../types'

const SENTIMENT: Record<SummarySentiment, { label: string; cls: string }> = {
  POSITIVE: { label: 'Positive', cls: 'bg-amber text-black' },
  MIXED: { label: 'Mixed', cls: 'border border-line bg-surface-2 text-muted' },
  NEGATIVE: { label: 'Negative', cls: 'bg-danger/15 text-danger border border-danger/40' },
}

function Heading() {
  return (
    <h2 id="summary-heading" className="flex items-center gap-2 text-lg font-bold">
      <Sparkles className="h-5 w-5 text-amber" aria-hidden />
      What CineCircle thinks
    </h2>
  )
}

function SummarySkeleton() {
  return (
    <section className="card p-4 sm:p-5" aria-label="Loading AI review summary" aria-busy="true">
      <div className="flex items-center justify-between">
        <div className="skeleton h-6 w-52" />
        <div className="skeleton h-6 w-20 rounded-full" />
      </div>
      <div className="mt-4 space-y-2">
        <div className="skeleton h-4 w-full" />
        <div className="skeleton h-4 w-11/12" />
        <div className="skeleton h-4 w-2/3" />
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <div className="skeleton h-24 rounded-xl" />
        <div className="skeleton h-24 rounded-xl" />
      </div>
      <div className="skeleton mt-4 h-3 w-48" />
    </section>
  )
}

function PointList({ kind, items }: { kind: 'pros' | 'cons'; items: string[] }) {
  const pro = kind === 'pros'
  const Icon = pro ? Plus : Minus
  return (
    <div className="rounded-xl border border-line bg-surface-2 p-3">
      <h3 className={cn('text-xs font-semibold uppercase tracking-wider', pro ? 'text-amber' : 'text-muted')}>
        {pro ? 'Pros' : 'Cons'}
      </h3>
      {items.length === 0 ? (
        <p className="mt-2 text-sm text-muted">{pro ? 'Nothing stood out.' : 'No real complaints.'}</p>
      ) : (
        <ul className="mt-2 space-y-1.5">
          {items.map((it, i) => (
            <li key={i} className="flex gap-2 text-sm text-ink/90">
              <Icon className={cn('mt-0.5 h-4 w-4 shrink-0', pro ? 'text-amber' : 'text-danger')} aria-hidden />
              <span>{it}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * "What CineCircle thinks" — AI summary of public, spoiler-free reviews (FEATURES-V2.md, Feature 1).
 * Errors never toast: NOT_ENOUGH_REVIEWS shows a playful empty state, anything else hides the card.
 */
export function MovieSummaryCard({
  tmdbId,
  writeHref,
  canWrite,
}: {
  tmdbId: number
  writeHref: string
  canWrite: boolean
}) {
  const q = useMovieSummary(tmdbId)

  if (q.isPending) return <SummarySkeleton />

  if (q.isError) {
    if (q.error instanceof ApiError && q.error.code === 'NOT_ENOUGH_REVIEWS') {
      return (
        <section className="card flex flex-col items-center gap-3 p-5 text-center sm:flex-row sm:text-left" aria-labelledby="summary-empty">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-amber-muted text-amber">
            <Sparkles className="h-6 w-6" aria-hidden />
          </div>
          <div className="min-w-0 flex-1">
            <h2 id="summary-empty" className="font-semibold">
              Need 3 reviews before we can read the room. Write one?
            </h2>
            <p className="mt-0.5 text-sm text-muted">Once enough people weigh in, our AI sums up the vibe here.</p>
          </div>
          <Link
            to={canWrite ? writeHref : '/login'}
            state={{ from: writeHref }}
            className="btn btn-soft shrink-0"
          >
            <PenLine className="h-4 w-4" aria-hidden /> Write a review
          </Link>
        </section>
      )
    }
    // SUMMARY_UNAVAILABLE (LLM off / failed), MOVIE_NOT_FOUND, network…: hide quietly.
    return null
  }

  const s = q.data
  const sentiment = SENTIMENT[s.sentiment] ?? SENTIMENT.MIXED

  return (
    <section className="card p-4 sm:p-5" aria-labelledby="summary-heading">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Heading />
        <span
          className={cn('rounded-full px-2.5 py-1 font-display text-xs font-bold uppercase tracking-wide', sentiment.cls)}
          aria-label={`Overall sentiment: ${sentiment.label}`}
        >
          {sentiment.label}
        </span>
      </div>

      <p className="mt-3 text-[15px] leading-relaxed text-ink/90">{s.summary}</p>

      {(s.pros.length > 0 || s.cons.length > 0) && (
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <PointList kind="pros" items={s.pros} />
          <PointList kind="cons" items={s.cons} />
        </div>
      )}

      {s.stale && (
        <p className="mt-3 flex items-center gap-1.5 text-xs text-amber/90">
          <AlertCircle className="h-3.5 w-3.5" aria-hidden />
          This summary may be out of date.
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-line pt-3 text-xs text-muted">
        <span>
          Based on {s.basedOnCount} {s.basedOnCount === 1 ? 'review' : 'reviews'}
          {s.avgRating !== null && <> · avg {formatScore(s.avgRating)}/10</>}
          {' · '}
          <time dateTime={s.generatedAt} title={fullDate(s.generatedAt)}>
            updated {timeAgo(s.generatedAt)}
          </time>
        </span>
        <span className="inline-flex items-center gap-1 rounded-full border border-line px-2 py-0.5 text-[11px] font-medium">
          <Sparkles className="h-3 w-3 text-amber" aria-hidden />
          AI-generated
        </span>
      </div>
    </section>
  )
}
