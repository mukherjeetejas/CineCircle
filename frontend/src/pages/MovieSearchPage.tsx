import { MessageSquare, Search, Star } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMovieSearch } from '../api/movies'
import { EmptyState, ErrorState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
import { Poster } from '../components/Poster'
import { MovieGridSkeleton } from '../components/Skeletons'
import { Spinner } from '../components/Spinner'
import { useInfiniteScroll } from '../lib/hooks'
import { formatScore } from '../lib/utils'
import type { MovieSummary } from '../types'

function MovieCard({ movie }: { movie: MovieSummary }) {
  return (
    <Link
      to={`/movies/${movie.tmdbId}`}
      className="group block rounded-2xl border border-transparent p-1.5 transition hover:border-line hover:bg-surface hover:shadow-glow"
    >
      <div className="relative">
        <Poster url={movie.posterUrl} title={movie.title} className="w-full rounded-xl" />
        {movie.tmdbVoteAverage !== null && movie.tmdbVoteAverage > 0 && (
          <span className="absolute left-2 top-2 rounded-full bg-black/75 px-2 py-0.5 text-[11px] font-semibold backdrop-blur">
            TMDB {formatScore(movie.tmdbVoteAverage)}
          </span>
        )}
      </div>
      <div className="px-1 pb-1 pt-2">
        <div className="line-clamp-2 text-sm font-semibold leading-snug group-hover:text-amber">{movie.title}</div>
        <div className="mt-1 flex items-center gap-2 text-xs text-muted">
          <span>{movie.releaseYear ?? '—'}</span>
          {movie.reviewCount > 0 ? (
            <>
              <span className="inline-flex items-center gap-0.5">
                <MessageSquare className="h-3 w-3" aria-hidden />
                {movie.reviewCount}
              </span>
              <span className="inline-flex items-center gap-0.5 font-semibold text-amber">
                <Star className="h-3 w-3 fill-amber" aria-hidden />
                {formatScore(movie.avgRating)}
              </span>
            </>
          ) : (
            <span className="italic">no reviews yet</span>
          )}
        </div>
      </div>
    </Link>
  )
}

export function MovieSearchPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const [text, setText] = useState(q)
  const timer = useRef<number | undefined>(undefined)

  // Keep the input in sync when ?q= changes from elsewhere (e.g. the global search bar).
  const [prevQ, setPrevQ] = useState(q)
  if (q !== prevQ) {
    setPrevQ(q)
    if (q !== text.trim()) setText(q)
  }

  // Debounce (300 ms) URL updates while typing.
  const onType = (value: string) => {
    setText(value)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => {
      const v = value.trim()
      setParams(v ? { q: v } : {}, { replace: true })
    }, 300)
  }
  useEffect(() => () => window.clearTimeout(timer.current), [])

  const query = useMovieSearch(q)
  const movies = query.data?.pages.flatMap((p) => p.items) ?? []
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = query
  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) void fetchNextPage()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])
  const sentinel = useInfiniteScroll<HTMLDivElement>(loadMore, !!hasNextPage)

  return (
    <div>
      <PageHeader title="Find a movie" subtitle="Search the whole TMDB catalogue and see what the circle thinks." />
      <div className="relative mb-5">
        <Search className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-muted" aria-hidden />
        <input
          type="search"
          value={text}
          onChange={(e) => onType(e.target.value)}
          placeholder="Dune, Past Lives, Paddington 2…"
          aria-label="Search movies"
          className="input rounded-full py-3 pl-12 text-base"
          autoFocus={!q}
        />
      </div>

      {!q ? (
        <EmptyState
          icon={<Search className="h-7 w-7" aria-hidden />}
          title="What are we watching?"
          description="Type a title to search. Every review on cinecircle is tied to a real TMDB movie."
        />
      ) : query.isPending ? (
        <MovieGridSkeleton />
      ) : query.isError ? (
        <ErrorState message="Movie search is unavailable right now." onRetry={() => void query.refetch()} />
      ) : movies.length === 0 ? (
        <EmptyState title={`Nothing found for “${q}”`} description="Plot hole. Try a different spelling or the original title." />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {movies.map((m) => (
              <MovieCard key={m.tmdbId} movie={m} />
            ))}
          </div>
          <div ref={sentinel} aria-hidden />
          {isFetchingNextPage && (
            <div className="flex justify-center py-6">
              <Spinner />
            </div>
          )}
        </>
      )}
    </div>
  )
}
