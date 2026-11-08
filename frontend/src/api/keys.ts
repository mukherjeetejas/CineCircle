import type { MoviePostSort, PopularWindow } from '../types'

/** Central query keys so invalidation stays consistent across hook files. */
export const qk = {
  me: ['me'] as const,
  user: (username: string) => ['users', username, 'profile'] as const,
  userPosts: (username: string) => ['users', username, 'posts'] as const,
  userFollows: (username: string, kind: 'followers' | 'following') => ['users', username, kind] as const,
  userSearch: (q: string) => ['user-search', q] as const,
  followRequests: ['follow-requests'] as const,
  suggestions: ['user-suggestions'] as const,

  movieSearch: (q: string) => ['movie-search', q, 'infinite'] as const,
  movieSearchPreview: (q: string) => ['movie-search', q, 'preview'] as const,
  trending: ['trending'] as const,
  movie: (tmdbId: number) => ['movies', tmdbId, 'detail'] as const,
  moviePosts: (tmdbId: number, sort: MoviePostSort) => ['movies', tmdbId, 'posts', sort] as const,

  feed: ['feed'] as const,
  popular: (window: PopularWindow) => ['popular', window] as const,
  popularAll: ['popular'] as const,
  post: (id: string) => ['post', id] as const,
  comments: (postId: string) => ['post', postId, 'comments'] as const,
}

export const PAGE_SIZE = 20
