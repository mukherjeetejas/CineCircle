import { Star, TrendingUp, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useTrendingMovies } from '../api/movies'
import { useSuggestions } from '../api/users'
import { useAuth } from '../auth/context'
import { FollowButton } from '../components/FollowButton'
import { Poster } from '../components/Poster'
import { MiniMovieSkeleton, UserListSkeleton } from '../components/Skeletons'
import { TmdbFooterLine } from '../components/TmdbAttribution'
import { UserRow } from '../components/UserRow'
import { formatScore } from '../lib/utils'

function TrendingMovies() {
  const { data, isPending, isError } = useTrendingMovies()
  return (
    <section className="card p-4" aria-labelledby="trending-heading">
      <h2 id="trending-heading" className="mb-3 flex items-center gap-2 text-sm font-bold uppercase tracking-wider text-muted">
        <TrendingUp className="h-4 w-4 text-amber" aria-hidden /> Trending this week
      </h2>
      {isPending ? (
        <MiniMovieSkeleton />
      ) : isError || !data?.length ? (
        <p className="text-sm text-muted">Trending is taking a popcorn break.</p>
      ) : (
        <ol className="space-y-1">
          {data.slice(0, 7).map((m, i) => (
            <li key={m.tmdbId}>
              <Link
                to={`/movies/${m.tmdbId}`}
                className="group flex items-center gap-3 rounded-xl p-1.5 transition-colors hover:bg-surface-2"
              >
                <span className="w-4 text-center font-display text-sm font-bold text-muted group-hover:text-amber">
                  {i + 1}
                </span>
                <Poster url={m.posterUrl} title={m.title} className="h-12 w-8 shrink-0 rounded-md" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium group-hover:text-amber">{m.title}</div>
                  <div className="flex items-center gap-2 text-xs text-muted">
                    <span>{m.releaseYear ?? '—'}</span>
                    {m.reviewCount > 0 && (
                      <span className="inline-flex items-center gap-0.5 text-amber">
                        <Star className="h-3 w-3 fill-amber" aria-hidden />
                        {formatScore(m.avgRating)}
                      </span>
                    )}
                  </div>
                </div>
              </Link>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

/** `GET /users/suggestions` (max 5). Hidden entirely when there's nothing to suggest. */
function WhoToFollow() {
  const { user, status } = useAuth()
  const { data, isPending, isError } = useSuggestions(status === 'loading' ? null : (user?.username ?? null))

  if (status === 'loading' || isError || (!isPending && (data?.length ?? 0) === 0)) return null

  return (
    <section className="card p-2" aria-labelledby="wtf-heading">
      <h2 id="wtf-heading" className="flex items-center gap-2 px-2 pb-1 pt-2 text-sm font-bold uppercase tracking-wider text-muted">
        <Users className="h-4 w-4 text-amber" aria-hidden /> Who to follow
      </h2>
      {isPending ? (
        <UserListSkeleton count={3} />
      ) : (
        <ul>
          {data.slice(0, 5).map((u) => (
            <li key={u.id}>
              <UserRow user={u} action={<FollowButton profile={u} size="sm" />} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export function RightRail() {
  return (
    <div className="space-y-4">
      <TrendingMovies />
      <WhoToFollow />
      <footer className="space-y-2 px-2 text-xs text-muted">
        <TmdbFooterLine />
        <p>
          <Link to="/about" className="hover:text-amber">
            About
          </Link>{' '}
          · © {new Date().getFullYear()} cinecircle
        </p>
      </footer>
    </div>
  )
}
