import { CalendarDays, Lock } from 'lucide-react'
import { useCallback } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { flattenUnique } from '../api/paging'
import { useUserFollows, useUserPosts, useUserProfile } from '../api/users'
import { Avatar } from '../components/Avatar'
import { EmptyState, ErrorState } from '../components/EmptyState'
import { FollowButton } from '../components/FollowButton'
import { PostList } from '../components/PostList'
import { UserListSkeleton } from '../components/Skeletons'
import { Tabs } from '../components/Tabs'
import { UserRow } from '../components/UserRow'
import { useInfiniteScroll } from '../lib/hooks'
import { compactNumber, joinedDate } from '../lib/utils'
import type { UserProfile } from '../types'

type Tab = 'reviews' | 'followers' | 'following'

function LockCard() {
  return (
    <EmptyState
      icon={<Lock className="h-7 w-7" aria-hidden />}
      title="This account is private."
      description="Follow to see their reviews."
    />
  )
}

function FollowList({ username, kind }: { username: string; kind: 'followers' | 'following' }) {
  const q = useUserFollows(username, kind, true)
  const users = flattenUnique(q.data)
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = q
  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) void fetchNextPage()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])
  const sentinel = useInfiniteScroll<HTMLDivElement>(loadMore, !!hasNextPage)

  if (q.isPending) return <div className="card p-2"><UserListSkeleton /></div>
  if (q.isError) {
    return (q.error as { status?: number }).status === 403 ? <LockCard /> : <ErrorState onRetry={() => void q.refetch()} />
  }
  if (users.length === 0) {
    return (
      <EmptyState
        title={kind === 'followers' ? 'No followers yet' : 'Not following anyone yet'}
        description={kind === 'followers' ? 'Every cult classic starts small.' : 'Their watchlist of people is empty.'}
      />
    )
  }
  return (
    <div className="card p-2">
      {users.map((u) => (
        <UserRow key={u.id} user={u} />
      ))}
      <div ref={sentinel} aria-hidden />
      {isFetchingNextPage && <UserListSkeleton count={2} />}
    </div>
  )
}

function ReviewsTab({ profile }: { profile: UserProfile }) {
  const posts = useUserPosts(profile.username, profile.canViewPosts)
  if (!profile.canViewPosts) return <LockCard />
  if (posts.isError && (posts.error as { status?: number }).status === 403) return <LockCard />
  const self = profile.followStatus === 'SELF'
  return (
    <PostList
      query={posts}
      empty={
        <EmptyState
          title="No reviews yet. Be the main character."
          description={self ? 'Your first hot take is one click away.' : `@${profile.username} hasn't posted a review yet.`}
          action={
            self && (
              <Link to="/submit" className="btn btn-primary">
                Write a review
              </Link>
            )
          }
        />
      }
    />
  )
}

function ProfileHeaderSkeleton() {
  return (
    <div className="card p-5" role="status" aria-label="Loading profile">
      <div className="flex items-center gap-4">
        <div className="skeleton h-24 w-24 rounded-full" />
        <div className="flex-1 space-y-2">
          <div className="skeleton h-6 w-40" />
          <div className="skeleton h-4 w-24" />
        </div>
      </div>
      <div className="skeleton mt-4 h-4 w-3/4" />
    </div>
  )
}

export function ProfilePage() {
  const { username = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const rawTab = params.get('tab')
  const tab: Tab = rawTab === 'followers' || rawTab === 'following' ? rawTab : 'reviews'
  const profile = useUserProfile(username)

  if (profile.isPending) return <ProfileHeaderSkeleton />
  if (profile.isError) {
    return (profile.error as { status?: number }).status === 404 ? (
      <EmptyState title="User not found" description={`There's nobody called @${username}. Yet.`} />
    ) : (
      <ErrorState onRetry={() => void profile.refetch()} />
    )
  }
  const p = profile.data

  const stat = (n: number, label: string, to?: Tab) => {
    const content = (
      <>
        <span className="font-display font-bold text-ink">{compactNumber(n)}</span> <span className="text-muted">{label}</span>
      </>
    )
    return to ? (
      <button type="button" className="rounded hover:underline" onClick={() => setParams({ tab: to }, { replace: true })}>
        {content}
      </button>
    ) : (
      <span>{content}</span>
    )
  }

  return (
    <div>
      <section className="card overflow-hidden" aria-labelledby="profile-name">
        <div className="h-20 bg-gradient-to-r from-amber-muted via-surface-2 to-amber-muted/40" />
        <div className="px-4 pb-5 sm:px-5">
          <div className="-mt-12 flex items-end justify-between gap-3">
            <Avatar url={p.avatarUrl} name={p.displayName || p.username} size="xl" className="ring-4 ring-surface" />
            <FollowButton profile={p} />
          </div>
          <h1 id="profile-name" className="mt-3 flex items-center gap-2 text-2xl font-bold">
            {p.displayName || p.username}
            {p.isPrivate && <Lock className="h-4 w-4 text-muted" aria-label="Private account" />}
          </h1>
          <div className="text-sm text-muted">@{p.username}</div>
          {p.bio && <p className="mt-3 whitespace-pre-line text-[15px]">{p.bio}</p>}
          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm">
            {stat(p.postCount, p.postCount === 1 ? 'review' : 'reviews')}
            {stat(p.followerCount, p.followerCount === 1 ? 'follower' : 'followers', 'followers')}
            {stat(p.followingCount, 'following', 'following')}
            <span className="inline-flex items-center gap-1 text-muted">
              <CalendarDays className="h-3.5 w-3.5" aria-hidden /> Joined {joinedDate(p.createdAt)}
            </span>
          </div>
        </div>
      </section>

      <Tabs
        label="Profile sections"
        idPrefix="profile"
        className="my-4"
        items={[
          { value: 'reviews', label: 'Reviews' },
          { value: 'followers', label: 'Followers' },
          { value: 'following', label: 'Following' },
        ]}
        value={tab}
        onChange={(t) => setParams(t === 'reviews' ? {} : { tab: t }, { replace: true })}
      />
      <div id="profile-panel" role="tabpanel" aria-labelledby={`profile-tab-${tab}`}>
        {tab === 'reviews' ? (
          <ReviewsTab profile={p} />
        ) : (
          // The server decides: 403 PRIVATE_ACCOUNT renders the lock card inside FollowList.
          <FollowList key={tab} username={p.username} kind={tab} />
        )}
      </div>
    </div>
  )
}
