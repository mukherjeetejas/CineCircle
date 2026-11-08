import { PenLine, Star } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { isValidTmdbId, useMovie, useMoviePosts } from '../api/movies'
import { useAuth } from '../auth/context'
import { EmptyState, ErrorState } from '../components/EmptyState'
import { MovieSummaryCard } from '../components/MovieSummaryCard'
import { Poster } from '../components/Poster'
import { PostList } from '../components/PostList'
import { Tabs } from '../components/Tabs'
import { formatScore } from '../lib/utils'
import type { MoviePostSort } from '../types'

function ScoreTile({ label, score, sub, accent }: { label: string; score: number | null; sub: string; accent?: boolean }) {
  return (
    <div className={accent ? 'rounded-2xl border border-amber/50 bg-amber-muted/40 p-3' : 'rounded-2xl border border-line bg-surface-2 p-3'}>
      <div className="text-[11px] font-semibold uppercase tracking-wider text-muted">{label}</div>
      <div className={`mt-1 flex items-baseline gap-1 font-display text-3xl font-bold ${accent ? 'text-amber' : 'text-ink'}`}>
        {formatScore(score)}
        <span className="text-sm font-medium text-muted">/10</span>
      </div>
      <div className="text-xs text-muted">{sub}</div>
    </div>
  )
}

export function MoviePage() {
  const { tmdbId: raw } = useParams()
  const tmdbId = Number(raw)
  const [params, setParams] = useSearchParams()
  const sort: MoviePostSort = params.get('sort') === 'new' ? 'new' : 'top'
  const { status } = useAuth()
  const movie = useMovie(tmdbId)
  const posts = useMoviePosts(tmdbId, sort)

  if (!isValidTmdbId(tmdbId)) {
    return <EmptyState title="That's not a movie we know." description="Check the link and try again." />
  }
  if (movie.isPending) {
    return (
      <div aria-label="Loading movie" role="status">
        <div className="skeleton h-48 w-full rounded-2xl" />
        <div className="-mt-16 flex gap-4 px-4">
          <div className="skeleton h-40 w-28 rounded-xl" />
          <div className="mt-20 flex-1 space-y-2">
            <div className="skeleton h-6 w-2/3" />
            <div className="skeleton h-4 w-1/3" />
          </div>
        </div>
      </div>
    )
  }
  if (movie.isError) {
    const notFound = (movie.error as { status?: number }).status === 404 || (movie.error as { code?: string }).code === 'MOVIE_NOT_FOUND'
    return notFound ? (
      <EmptyState title="Movie not found" description="TMDB doesn't know this one. Maybe it's still in development hell." />
    ) : (
      <ErrorState onRetry={() => void movie.refetch()} />
    )
  }
  const m = movie.data
  const writeHref = `/submit?tmdbId=${m.tmdbId}`

  return (
    <div>
      {/* hero */}
      <section className="card overflow-hidden" aria-labelledby="movie-title">
        <div className="relative h-44 bg-surface-2 sm:h-56">
          {m.backdropUrl && <img src={m.backdropUrl} alt="" className="h-full w-full object-cover opacity-70" />}
          <div className="absolute inset-0 bg-gradient-to-t from-surface via-surface/40 to-transparent" />
        </div>
        <div className="relative -mt-20 flex gap-4 px-4 pb-4 sm:px-5">
          <Poster url={m.posterUrl} title={m.title} className="w-28 shrink-0 rounded-xl shadow-2xl ring-1 ring-line sm:w-36" />
          <div className="min-w-0 self-end pb-1">
            <h1 id="movie-title" className="text-2xl font-bold leading-tight sm:text-3xl">
              {m.title}
            </h1>
            <div className="mt-1 text-sm text-muted">{m.releaseYear ?? 'Release year unknown'}</div>
            {m.genres.length > 0 && (
              <ul className="mt-2 flex flex-wrap gap-1.5" aria-label="Genres">
                {m.genres.map((g) => (
                  <li key={g} className="chip">
                    {g}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
        <div className="space-y-4 px-4 pb-5 sm:px-5">
          {m.overview && <p className="text-[15px] leading-relaxed text-ink/85">{m.overview}</p>}
          <div className="grid grid-cols-2 gap-3">
            <ScoreTile label="TMDB score" score={m.tmdbVoteAverage} sub="from TMDB voters" />
            <ScoreTile
              label="CineCircle score"
              score={m.reviewCount > 0 ? m.avgRating : null}
              sub={m.reviewCount === 1 ? '1 review' : `${m.reviewCount} reviews`}
              accent
            />
          </div>
          <Link to={status === 'authenticated' ? writeHref : '/login'} state={{ from: writeHref }} className="btn btn-primary w-full sm:w-auto">
            <PenLine className="h-4 w-4" aria-hidden /> Write a review
          </Link>
        </div>
      </section>

      {/* AI summary (above the review list) */}
      <div className="mt-6 empty:hidden">
        <MovieSummaryCard tmdbId={m.tmdbId} writeHref={writeHref} canWrite={status === 'authenticated'} />
      </div>

      {/* reviews */}
      <div className="mb-3 mt-6 flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-bold">
          <Star className="h-5 w-5 fill-amber text-amber" aria-hidden /> Reviews
        </h2>
        <Tabs
          label="Sort reviews"
          idPrefix="movie-sort"
          items={[
            { value: 'top', label: 'Top' },
            { value: 'new', label: 'New' },
          ]}
          value={sort}
          onChange={(s) => setParams({ sort: s }, { replace: true })}
          className="w-44"
        />
      </div>
      <div id="movie-sort-panel" role="tabpanel" aria-labelledby={`movie-sort-tab-${sort}`}>
        <PostList
          query={posts}
          empty={
            <EmptyState
              title="No reviews yet. Be the main character."
              description={`Nobody has reviewed ${m.title} here. First take wins.`}
              action={
                <Link to={writeHref} className="btn btn-primary">
                  Write the first review
                </Link>
              }
            />
          }
        />
      </div>
    </div>
  )
}
