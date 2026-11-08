import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { apiFetch } from './client'
import { PAGE_SIZE, qk } from './keys'
import { firstPage, nextPageParam } from './paging'
import { adjustPostCommentCount } from './posts'
import type { Comment, CreateCommentRequest, Page } from '../types'

export function useComments(postId: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: qk.comments(postId),
    queryFn: ({ pageParam }) =>
      apiFetch<Page<Comment>>(`/posts/${encodeURIComponent(postId)}/comments`, {
        query: { page: pageParam, size: PAGE_SIZE },
      }),
    initialPageParam: firstPage,
    getNextPageParam: nextPageParam,
    enabled,
  })
}

export function useCreateComment(postId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateCommentRequest) =>
      apiFetch<Comment>(`/posts/${encodeURIComponent(postId)}/comments`, { method: 'POST', json: body }),
    onSuccess: () => {
      adjustPostCommentCount(qc, postId, 1)
      qc.invalidateQueries({ queryKey: qk.comments(postId) })
    },
  })
}

export function useDeleteComment(postId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (commentId: string) =>
      apiFetch<void>(`/comments/${encodeURIComponent(commentId)}`, { method: 'DELETE' }),
    onSuccess: () => {
      toast.success('Comment deleted')
      // Backend decrements commentCount on soft delete: mirror it in every cached copy (lists + detail).
      adjustPostCommentCount(qc, postId, -1)
      qc.invalidateQueries({ queryKey: qk.comments(postId) })
    },
  })
}
