import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import { PAGE_SIZE, qk } from './keys'
import { firstPage, nextPageParam } from './paging'
import type { MovieDetail, MoviePostSort, MovieSummary, Page, PostSummary, ReviewSummary } from '../types'

export function searchMovies(q: string, page = 0, signal?: AbortSignal) {
  return apiFetch<Page<MovieSummary>>('/movies/search', { query: { q, page }, signal, silent: true })
}

/** First page only — used by autocomplete / global search dropdown. */
export function useMovieSearchPreview(q: string) {
  const term = q.trim()
  return useQuery({
    queryKey: qk.movieSearchPreview(term),
    queryFn: ({ signal }) => searchMovies(term, 0, signal),
    enabled: term.length >= 2,
    staleTime: 5 * 60_000,
  })
}

export function useMovieSearch(q: string) {
  const term = q.trim()
  return useInfiniteQuery({
    queryKey: qk.movieSearch(term),
    queryFn: ({ pageParam, signal }) => searchMovies(term, pageParam, signal),
    initialPageParam: firstPage,
    getNextPageParam: nextPageParam,
    enabled: term.length >= 1,
    staleTime: 5 * 60_000,
  })
}

export function useTrendingMovies() {
  return useQuery({
    queryKey: qk.trending,
    queryFn: () => apiFetch<MovieSummary[]>('/movies/trending', { silent: true }),
    staleTime: 30 * 60_000,
  })
}

/** TMDB ids are positive integers; anything else (e.g. /movies/abc → NaN) must never hit the API. */
export function isValidTmdbId(id: number | undefined): id is number {
  return id !== undefined && Number.isInteger(id) && id > 0
}

export function useMovie(tmdbId: number | undefined) {
  return useQuery({
    queryKey: qk.movie(isValidTmdbId(tmdbId) ? tmdbId : -1),
    queryFn: () => apiFetch<MovieDetail>(`/movies/${tmdbId}`, { silent: true }),
    enabled: isValidTmdbId(tmdbId),
    staleTime: 5 * 60_000,
    retry: false,
  })
}

/**
 * AI review summary. `silent`: this query never triggers the global error toast — the card handles
 * every error itself (NOT_ENOUGH_REVIEWS → empty state, SUMMARY_UNAVAILABLE and anything else → hidden).
 * No retries: a 502 means the LLM call already failed server-side; retrying just doubles the wait.
 */
export function useMovieSummary(tmdbId: number | undefined) {
  return useQuery({
    queryKey: qk.movieSummary(isValidTmdbId(tmdbId) ? tmdbId : -1),
    queryFn: ({ signal }) => apiFetch<ReviewSummary>(`/movies/${tmdbId}/summary`, { silent: true, signal }),
    enabled: isValidTmdbId(tmdbId),
    staleTime: 10 * 60_000,
    retry: false,
  })
}

export function useMoviePosts(tmdbId: number, sort: MoviePostSort) {
  return useInfiniteQuery({
    queryKey: qk.moviePosts(tmdbId, sort),
    queryFn: ({ pageParam }) =>
      apiFetch<Page<PostSummary>>(`/movies/${tmdbId}/posts`, { query: { sort, page: pageParam, size: PAGE_SIZE } }),
    initialPageParam: firstPage,
    getNextPageParam: nextPageParam,
    enabled: isValidTmdbId(tmdbId),
  })
}
