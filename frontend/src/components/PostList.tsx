import type { InfiniteData, UseInfiniteQueryResult } from '@tanstack/react-query'
import { Fragment, useCallback, type ReactNode } from 'react'
import { flattenUnique } from '../api/paging'
import { useInfiniteScroll } from '../lib/hooks'
import type { Page, PostSummary } from '../types'
import { ErrorState } from './EmptyState'
import { PostCard } from './PostCard'
import { PostCardSkeleton, PostListSkeleton } from './Skeletons'

interface Props {
  query: UseInfiniteQueryResult<InfiniteData<Page<PostSummary>>, Error>
  empty: ReactNode
  /** Optional divider rendered before `item` (given the previous item). */
  divider?: (prev: PostSummary | undefined, item: PostSummary) => ReactNode
}

export function PostList({ query, empty, divider }: Props) {
  const { data, isPending, isError, refetch, hasNextPage, isFetchingNextPage, fetchNextPage } = query
  const posts = flattenUnique(data)

  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) void fetchNextPage()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])
  const sentinel = useInfiniteScroll<HTMLDivElement>(loadMore, !!hasNextPage)

  if (isPending) return <PostListSkeleton />
  if (isError && posts.length === 0) return <ErrorState onRetry={() => void refetch()} />
  if (posts.length === 0) return <>{empty}</>

  return (
    <div className="space-y-3">
      {posts.map((post, i) => (
        <Fragment key={post.id}>
          {divider?.(posts[i - 1], post)}
          <PostCard post={post} />
        </Fragment>
      ))}
      <div ref={sentinel} aria-hidden />
      {isFetchingNextPage && <PostCardSkeleton />}
      {hasNextPage && !isFetchingNextPage && (
        <div className="flex justify-center">
          <button type="button" className="btn btn-outline btn-sm" onClick={loadMore}>
            Load more
          </button>
        </div>
      )}
      {!hasNextPage && posts.length > 3 && (
        <p className="py-6 text-center text-sm text-muted">
          You've reached the end credits<span className="text-amber">.</span>
        </p>
      )}
    </div>
  )
}
