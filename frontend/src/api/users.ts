import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { apiFetch } from './client'
import { PAGE_SIZE, qk } from './keys'
import { firstPage, nextPageParam } from './paging'
import type {
  FollowResponse,
  FollowStatus,
  Page,
  PostSummary,
  UpdateMeRequest,
  UserProfile,
  UserSummary,
} from '../types'

const enc = encodeURIComponent

export function useUserProfile(username: string | undefined) {
  return useQuery({
    queryKey: qk.user(username ?? ''),
    queryFn: () => apiFetch<UserProfile>(`/users/${enc(username!)}`, { silent: true }),
    enabled: !!username,
    retry: false, // apiFetch already retries GETs while the server wakes up
  })
}

export function useUserPosts(username: string, enabled: boolean) {
  return useInfiniteQuery({
    queryKey: qk.userPosts(username),
    queryFn: ({ pageParam }) =>
      apiFetch<Page<PostSummary>>(`/users/${enc(username)}/posts`, {
        query: { page: pageParam, size: PAGE_SIZE },
        silent: true, // 403 PRIVATE_ACCOUNT is rendered as a lock card
      }),
    initialPageParam: firstPage,
    getNextPageParam: nextPageParam,
    enabled,
    retry: false,
  })
}

export function useUserFollows(username: string, kind: 'followers' | 'following', enabled: boolean) {
  return useInfiniteQuery({
    queryKey: qk.userFollows(username, kind),
    queryFn: ({ pageParam }) =>
      apiFetch<Page<UserSummary>>(`/users/${enc(username)}/${kind}`, {
        query: { page: pageParam, size: PAGE_SIZE },
        silent: true,
      }),
    initialPageParam: firstPage,
    getNextPageParam: nextPageParam,
    enabled,
    retry: false,
  })
}

export function searchUsers(q: string, signal?: AbortSignal) {
  return apiFetch<UserSummary[]>('/users/search', { query: { q }, signal, silent: true })
}

export function useUserSearch(q: string) {
  const term = q.trim()
  return useQuery({
    queryKey: qk.userSearch(term),
    queryFn: ({ signal }) => searchUsers(term, signal),
    enabled: term.length >= 1,
    staleTime: 30_000,
  })
}

// ----- Follow / unfollow (optimistic) -----

interface FollowVars {
  username: string
  isPrivate: boolean
  action: 'follow' | 'unfollow'
  /** Status before the click (to word the toast: unfollow vs cancel request). */
  from: FollowStatus
}

function applyFollow(p: UserProfile, isPrivate: boolean, action: FollowVars['action']): UserProfile {
  if (action === 'follow') {
    return {
      ...p,
      followStatus: isPrivate ? 'PENDING' : 'ACCEPTED',
      followerCount: p.followerCount + (isPrivate ? 0 : 1),
      canViewPosts: isPrivate ? p.canViewPosts : true,
    }
  }
  return {
    ...p,
    followStatus: 'NONE',
    followerCount: Math.max(0, p.followerCount - (p.followStatus === 'ACCEPTED' ? 1 : 0)),
    canViewPosts: isPrivate ? false : p.canViewPosts,
  }
}

export function useFollowMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ username, action }: FollowVars) =>
      apiFetch<FollowResponse>(`/users/${enc(username)}/follow`, { method: action === 'follow' ? 'POST' : 'DELETE' }),
    onMutate: async ({ username, isPrivate, action }) => {
      const key = qk.user(username)
      await qc.cancelQueries({ queryKey: key })
      await qc.cancelQueries({ queryKey: qk.suggestions })
      const previous = qc.getQueryData<UserProfile>(key)
      const previousSuggestions = qc.getQueriesData<UserProfile[]>({ queryKey: qk.suggestions })
      if (previous) qc.setQueryData(key, applyFollow(previous, isPrivate, action))
      // Suggestions stay visible until their next natural refetch, showing the new button state.
      qc.setQueriesData<UserProfile[]>({ queryKey: qk.suggestions }, (old) =>
        old?.map((u) => (u.username === username ? applyFollow(u, isPrivate, action) : u)),
      )
      return { previous, previousSuggestions }
    },
    onSuccess: (res, { username, action, from }) => {
      const setStatus = (p: UserProfile) => ({ ...p, followStatus: res.followStatus })
      qc.setQueryData<UserProfile>(qk.user(username), (old) => (old ? setStatus(old) : old))
      qc.setQueriesData<UserProfile[]>({ queryKey: qk.suggestions }, (old) =>
        old?.map((u) => (u.username === username ? setStatus(u) : u)),
      )
      if (action === 'follow') {
        toast.success(res.followStatus === 'PENDING' ? `Request sent to @${username}` : `Following @${username}`)
      } else {
        toast.success(from === 'PENDING' ? `Request to @${username} cancelled` : `Unfollowed @${username}`)
      }
    },
    onError: (_err, { username }, ctx) => {
      if (ctx?.previous) qc.setQueryData(qk.user(username), ctx.previous)
      ctx?.previousSuggestions.forEach(([key, data]) => qc.setQueryData(key, data))
    },
    onSettled: (_d, _e, { username }) => {
      qc.invalidateQueries({ queryKey: qk.user(username) })
      qc.invalidateQueries({ queryKey: qk.userPosts(username) })
      qc.invalidateQueries({ queryKey: qk.userFollows(username, 'followers') })
      qc.invalidateQueries({ queryKey: qk.userFollows(username, 'following') })
      qc.invalidateQueries({ queryKey: qk.me })
      qc.invalidateQueries({ queryKey: qk.feed })
    },
  })
}

// ----- Suggestions ("Who to follow") -----

export function useSuggestions(viewer: string | null) {
  return useQuery({
    // Keyed per viewer: the list excludes the viewer and people they already follow.
    queryKey: [...qk.suggestions, viewer ?? 'anon'] as const,
    queryFn: () => apiFetch<UserProfile[]>('/users/suggestions', { silent: true }),
    staleTime: 5 * 60_000,
  })
}

// ----- Follow requests -----

export function useFollowRequests(enabled: boolean) {
  return useQuery({
    queryKey: qk.followRequests,
    queryFn: () => apiFetch<UserSummary[]>('/users/me/follow-requests'),
    enabled,
    refetchInterval: enabled ? 60_000 : false,
  })
}

export function useRespondFollowRequest() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ username, action }: { username: string; action: 'accept' | 'reject' }) =>
      apiFetch<void>(`/users/me/follow-requests/${enc(username)}/${action}`, { method: 'POST' }),
    onMutate: async ({ username }) => {
      await qc.cancelQueries({ queryKey: qk.followRequests })
      const previous = qc.getQueryData<UserSummary[]>(qk.followRequests)
      qc.setQueryData<UserSummary[]>(qk.followRequests, (old) => old?.filter((u) => u.username !== username))
      return { previous }
    },
    onSuccess: (_d, { username, action }) => {
      toast.success(action === 'accept' ? `@${username} can now see your reviews` : `Request from @${username} declined`)
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.previous) qc.setQueryData(qk.followRequests, ctx.previous)
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: qk.followRequests })
      qc.invalidateQueries({ queryKey: qk.me })
      qc.invalidateQueries({ queryKey: ['users'] })
    },
  })
}

// ----- Settings -----

export function useUpdateMe() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateMeRequest) =>
      apiFetch<UserProfile>('/users/me', { method: 'PATCH', json: body, silent: true }),
    onSuccess: (profile) => {
      qc.setQueryData<UserProfile>(qk.me, (old) => (old ? { ...old, ...profile } : profile))
      qc.invalidateQueries({ queryKey: ['users'] })
    },
  })
}

export function useUploadAvatar() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return apiFetch<UserProfile>('/users/me/avatar', { method: 'PUT', form })
    },
    onSuccess: (profile) => {
      // Contract: returns the updated UserProfile — show the new avatar immediately.
      qc.setQueryData<UserProfile>(qk.me, (old) => (old ? { ...old, ...profile } : profile))
      qc.invalidateQueries({ queryKey: ['users'] })
    },
  })
}
