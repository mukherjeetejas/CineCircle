import { Lock, TrendingUp, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useTrendingMovies } from '../api/movies'
import { useSuggestions } from '../api/users'
import { useAuth } from '../auth/context'
import { cn } from '../lib/utils'
import { Avatar } from './Avatar'
import { FollowButton } from './FollowButton'
import { Poster } from './Poster'

/**
 * Compact, horizontally scrolling versions of the right-rail widgets, for screens < 1024 px
 * where the right rail is hidden. Each strip hides itself when it has nothing to show.
 */

function StripHeading({ id, icon, children }: { id: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <h2 id={id} className="mb-2 flex items-center gap-2 px-1 text-xs font-bold uppercase tracking-wider text-muted">
      {icon}
      {children}
    </h2>
  )
}

const scroller = 'scrollbar-none -mx-3 flex snap-x gap-3 overflow-x-auto px-3 pb-1 sm:-mx-4 sm:px-4 md:mx-0 md:px-0'

export function TrendingStrip() {
  const { data, isPending, isError } = useTrendingMovies()
  if (isError || (!isPending && !data?.length)) return null

  return (
    <section aria-labelledby="trending-strip-heading">
      <StripHeading id="trending-strip-heading" icon={<TrendingUp className="h-3.5 w-3.5 text-amber" aria-hidden />}>
        Trending this week
      </StripHeading>
      <ol className={scroller}>
        {isPending
          ? Array.from({ length: 6 }, (_, i) => (
              <li key={i} className="w-20 shrink-0" aria-hidden>
                <div className="skeleton aspect-[2/3] w-full rounded-xl" />
                <div className="skeleton mt-1.5 h-2.5 w-3/4" />
              </li>
            ))
          : data.slice(0, 10).map((m, i) => (
              <li key={m.tmdbId} className="w-20 shrink-0 snap-start">
                <Link to={`/movies/${m.tmdbId}`} className="group block rounded-xl" title={m.title}>
                  <div className="relative">
                    <Poster
                      url={m.posterUrl}
                      title={m.title}
                      className="w-full rounded-xl ring-1 ring-line transition group-hover:ring-amber/70"
                    />
                    <span className="absolute -left-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-amber font-display text-[11px] font-bold text-black">
                      {i + 1}
                    </span>
                  </div>
                  <div className="mt-1 truncate text-[11px] font-medium text-ink/85 group-hover:text-amber">{m.title}</div>
                </Link>
              </li>
            ))}
      </ol>
    </section>
  )
}

export function SuggestionsStrip() {
  const { user, status } = useAuth()
  const { data, isPending, isError } = useSuggestions(status === 'loading' ? null : (user?.username ?? null))
  if (status === 'loading' || isError || (!isPending && !data?.length)) return null

  return (
    <section aria-labelledby="wtf-strip-heading">
      <StripHeading id="wtf-strip-heading" icon={<Users className="h-3.5 w-3.5 text-amber" aria-hidden />}>
        Who to follow
      </StripHeading>
      <ul className={scroller}>
        {isPending
          ? Array.from({ length: 3 }, (_, i) => (
              <li key={i} className="card flex w-40 shrink-0 flex-col items-center gap-2 p-3" aria-hidden>
                <div className="skeleton h-12 w-12 rounded-full" />
                <div className="skeleton h-3 w-20" />
                <div className="skeleton h-7 w-24 rounded-full" />
              </li>
            ))
          : data.slice(0, 5).map((u) => (
              <li key={u.id} className="card flex w-40 shrink-0 snap-start flex-col items-center p-3 text-center">
                <Link to={`/u/${u.username}`} className="flex w-full flex-col items-center rounded-lg">
                  <Avatar url={u.avatarUrl} name={u.displayName || u.username} size="lg" />
                  <span className="mt-2 flex w-full items-center justify-center gap-1 text-sm font-semibold">
                    <span className="truncate">{u.displayName || u.username}</span>
                    {u.isPrivate && <Lock className="h-3 w-3 shrink-0 text-muted" aria-label="Private account" />}
                  </span>
                  <span className="w-full truncate text-xs text-muted">@{u.username}</span>
                </Link>
                <FollowButton profile={u} size="sm" className="mt-2.5 w-full" />
              </li>
            ))}
      </ul>
    </section>
  )
}

/** Wrapper shown only below the `lg` breakpoint (where the right rail is hidden). */
export function MobileDiscover({ suggestions = false, className }: { suggestions?: boolean; className?: string }) {
  return (
    <div className={cn('space-y-5 lg:hidden', className)}>
      <TrendingStrip />
      {suggestions && <SuggestionsStrip />}
    </div>
  )
}
