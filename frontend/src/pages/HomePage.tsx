import { Flame, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useFeed } from '../api/posts'
import { useAuth } from '../auth/context'
import { EmptyState } from '../components/EmptyState'
import { MobileDiscover } from '../components/DiscoverStrips'
import { PageHeader } from '../components/PageHeader'
import { PostList } from '../components/PostList'
import type { PostSummary } from '../types'

function SourceDivider({ source }: { source: PostSummary['source'] }) {
  const following = source === 'FOLLOWING'
  return (
    <div className="flex items-center gap-3 pt-2 text-xs font-semibold uppercase tracking-wider text-muted" role="separator">
      {following ? <Users className="h-3.5 w-3.5 text-amber" aria-hidden /> : <Flame className="h-3.5 w-3.5 text-amber" aria-hidden />}
      <span>{following ? 'From people you follow' : 'Popular'}</span>
      <span className="h-px flex-1 bg-line" />
    </div>
  )
}

export function HomePage() {
  const { user } = useAuth()
  const feed = useFeed(true)

  return (
    <div>
      <PageHeader
        title={
          <>
            Hey {user?.displayName?.split(' ')[0] || user?.username}
            <span className="text-amber">.</span>
          </>
        }
        subtitle="Fresh takes from your circle, plus what's hot."
      />
      <MobileDiscover className="mb-5" />
      <PostList
        query={feed}
        divider={(prev, item) => {
          if (!item.source) return null
          if (prev?.source === item.source) return null
          return <SourceDivider source={item.source} />
        }}
        empty={
          <EmptyState
            title="No reviews yet. Be the main character."
            description="Follow some people or post your first review to get the feed rolling."
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <Link to="/submit" className="btn btn-primary">
                  Write a review
                </Link>
                <Link to="/popular" className="btn btn-outline">
                  Browse popular
                </Link>
              </div>
            }
          />
        }
      />
    </div>
  )
}
