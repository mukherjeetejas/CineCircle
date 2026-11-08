import { Flame } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { usePopular } from '../api/posts'
import { EmptyState } from '../components/EmptyState'
import { MobileDiscover } from '../components/DiscoverStrips'
import { PageHeader } from '../components/PageHeader'
import { PostList } from '../components/PostList'
import { Tabs } from '../components/Tabs'
import type { PopularWindow } from '../types'

const WINDOWS: { value: PopularWindow; label: string }[] = [
  { value: 'day', label: 'Today' },
  { value: 'week', label: 'This week' },
  { value: 'all', label: 'All time' },
]

function isWindow(v: string | null): v is PopularWindow {
  return v === 'day' || v === 'week' || v === 'all'
}

export function PopularPage() {
  const [params, setParams] = useSearchParams()
  const raw = params.get('window')
  const win: PopularWindow = isWindow(raw) ? raw : 'week'
  const query = usePopular(win)

  return (
    <div>
      <PageHeader
        title={
          <span className="inline-flex items-center gap-2">
            <Flame className="h-7 w-7 text-amber" aria-hidden /> Popular
          </span>
        }
        subtitle="The reviews everyone's upvoting."
      />
      <MobileDiscover suggestions className="mb-5" />
      <Tabs
        label="Time window"
        idPrefix="popular"
        items={WINDOWS}
        value={win}
        onChange={(w) => setParams({ window: w }, { replace: true })}
        className="mb-4"
      />
      <div id="popular-panel" role="tabpanel" aria-labelledby={`popular-tab-${win}`}>
        <PostList
          query={query}
          empty={
            <EmptyState
              title="No reviews yet. Be the main character."
              description={win === 'day' ? 'Nothing popped off today… yet.' : 'The stage is empty.'}
              action={
                <Link to="/submit" className="btn btn-primary">
                  Write the first one
                </Link>
              }
            />
          }
        />
      </div>
    </div>
  )
}
