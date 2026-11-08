import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
  type QueryKey,
} from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { apiFetch, jsonBlob } from './client'
import { PAGE_SIZE, qk } from './keys'
import { firstPage, isInfinitePage, nextPageParam } from './paging'
import type {
  CreatePostRequest,
  Page,
  PopularWindow,
  PostDetail,
  PostSummary,
  UpvoteResponse,
} from '../types'

export function useFeed(enabled: boolean) {
  return useInfiniteQuery({
    queryKey: qk.feed,
    queryFn: ({ pageParam }) =>
      apiFetch<Page<PostSummary>>('/feed', { query: { page: pageParam, size: PAGE_SIZE } }),
    initialPageParam: firstPage,
    getNextPageParam: nextPageParam,
    enabled,
  })
}

export function usePopular(window: PopularWindow) {
  return useInfiniteQuery({
    queryKey: qk.popular(window),
    queryFn: ({ pageParam }) =>
      apiFetch<Page<PostSummary>>('/posts/popular', { query: { window, page: pageParam, size: PAGE_SIZE } }),
    initialPageParam: firstPage,
    getNextPageParam: nextPageParam,
  })
}

export function usePost(id: string | undefined) {
  return useQuery({
    queryKey: qk.post(id ?? ''),
    queryFn: () => apiFetch<PostDetail>(`/posts/${encodeURIComponent(id!)}`, { silent: true }),
    enabled: !!id,
    retry: false,
  })
}

// ----- Cache helpers: a post can live in many lists at once -----

type Snapshot = Array<[QueryKey, unknown]>

function isPostLike(v: unknown): v is PostSummary {
  return typeof v === 'object' && v !== null && 'id' in v && 'upvoteCount' in v && 'movie' in v
}

/** Apply `fn` to every cached copy of post `postId` (lists + detail). Returns a snapshot for rollback. */
function updatePostEverywhere(qc: QueryClient, postId: string, fn: (p: PostSummary) => PostSummary): Snapshot {
  const snapshot: Snapshot = []
  for (const query of qc.getQueryCache().getAll()) {
    const data = query.state.data
    if (data === undefined) continue
    if (isInfinitePage(data)) {
      let touched = false
      const pages = data.pages.map((p) => ({
        ...p,
        items: p.items.map((item) => {
          if (isPostLike(item) && item.id === postId) {
            touched = true
            return fn(item)
          }
          return item
        }),
      }))
      if (touched) {
        snapshot.push([query.queryKey, data])
        qc.setQueryData(query.queryKey, { ...data, pages })
      }
    } else if (isPostLike(data) && data.id === postId) {
      snapshot.push([query.queryKey, data])
      qc.setQueryData(query.queryKey, fn(data))
    }
  }
  return snapshot
}

function queriesContainingPost(qc: QueryClient, postId: string): QueryKey[] {
  return qc
    .getQueryCache()
    .getAll()
    .filter(({ state: { data } }) =>
      isInfinitePage(data)
        ? data.pages.some((p) => p.items.some((i) => isPostLike(i) && i.id === postId))
        : isPostLike(data) && data.id === postId,
    )
    .map((q) => q.queryKey)
}

function removePostEverywhere(qc: QueryClient, postId: string) {
  for (const query of qc.getQueryCache().getAll()) {
    const data = query.state.data
    if (isInfinitePage(data) && data.pages.some((p) => p.items.some((i) => isPostLike(i) && i.id === postId))) {
      qc.setQueryData(query.queryKey, {
        ...data,
        pages: data.pages.map((p) => ({ ...p, items: p.items.filter((i) => !(isPostLike(i) && i.id === postId)) })),
      })
    }
  }
  // Don't remove the detail query here: the detail page is still mounted and navigates away
  // in its own onSuccess. Mark it stale without refetching so it can't be served again.
  void qc.invalidateQueries({ queryKey: qk.post(postId), exact: true, refetchType: 'none' })
}

function restore(qc: QueryClient, snapshot: Snapshot | undefined) {
  snapshot?.forEach(([key, data]) => qc.setQueryData(key, data))
}

export function adjustPostCommentCount(qc: QueryClient, postId: string, delta: number) {
  updatePostEverywhere(qc, postId, (p) => ({ ...p, commentCount: Math.max(0, p.commentCount + delta) }))
}

// ----- Upvote (optimistic, rollback on error) -----

export function useUpvote() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ postId, upvote }: { postId: string; upvote: boolean }) =>
      apiFetch<UpvoteResponse>(`/posts/${encodeURIComponent(postId)}/upvote`, { method: upvote ? 'PUT' : 'DELETE' }),
    onMutate: async ({ postId, upvote }) => {
      // Stop in-flight fetches of any list/detail holding this post so they can't overwrite the optimistic value.
      await Promise.all(
        queriesContainingPost(qc, postId).map((queryKey) => qc.cancelQueries({ queryKey, exact: true })),
      )
      const snapshot = updatePostEverywhere(qc, postId, (p) =>
        p.upvotedByMe === upvote
          ? p
          : { ...p, upvotedByMe: upvote, upvoteCount: Math.max(0, p.upvoteCount + (upvote ? 1 : -1)) },
      )
      return { snapshot }
    },
    onSuccess: (res, { postId }) => {
      updatePostEverywhere(qc, postId, (p) => ({ ...p, upvotedByMe: res.upvoted, upvoteCount: res.upvoteCount }))
    },
    onError: (_e, _v, ctx) => restore(qc, ctx?.snapshot),
  })
}

// ----- Create / delete -----

export function useCreatePost() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ post, image }: { post: CreatePostRequest; image?: File | null }) => {
      const form = new FormData()
      form.append('post', jsonBlob(post))
      if (image) form.append('image', image, image.name)
      return apiFetch<PostDetail>('/posts', { method: 'POST', form, silent: true })
    },
    onSuccess: (created) => {
      qc.setQueryData(qk.post(created.id), created)
      qc.invalidateQueries({ queryKey: qk.feed })
      qc.invalidateQueries({ queryKey: qk.popularAll })
      qc.invalidateQueries({ queryKey: ['movies', created.movie.tmdbId] })
      qc.invalidateQueries({ queryKey: qk.userPosts(created.author.username) })
      qc.invalidateQueries({ queryKey: qk.user(created.author.username) })
      qc.invalidateQueries({ queryKey: qk.me })
    },
  })
}

export function useDeletePost() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (post: PostSummary) => apiFetch<void>(`/posts/${encodeURIComponent(post.id)}`, { method: 'DELETE' }),
    onSuccess: (_d, post) => {
      removePostEverywhere(qc, post.id)
      toast.success('Review deleted')
      qc.invalidateQueries({ queryKey: qk.feed })
      qc.invalidateQueries({ queryKey: qk.popularAll })
      qc.invalidateQueries({ queryKey: ['movies', post.movie.tmdbId] })
      qc.invalidateQueries({ queryKey: qk.userPosts(post.author.username) })
      qc.invalidateQueries({ queryKey: qk.user(post.author.username) })
      qc.invalidateQueries({ queryKey: qk.me })
    },
  })
}
