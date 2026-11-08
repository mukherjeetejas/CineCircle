import { ArrowBigUp, Lock } from 'lucide-react'
import toast from 'react-hot-toast'
import { useLocation, useNavigate } from 'react-router-dom'
import { useUpvote } from '../api/posts'
import { useAuth } from '../auth/context'
import { cn, compactNumber } from '../lib/utils'
import type { PostSummary } from '../types'

interface Props {
  post: PostSummary
  orientation?: 'vertical' | 'horizontal'
}

export function UpvoteButton({ post, orientation = 'vertical' }: Props) {
  const { status } = useAuth()
  const upvote = useUpvote()
  const navigate = useNavigate()
  const location = useLocation()
  const loggedOut = status !== 'authenticated'
  const active = post.upvotedByMe

  const onClick = () => {
    if (loggedOut) {
      toast('Log in to upvote reviews', {
        icon: <Lock className="h-4 w-4 text-amber" aria-hidden />,
        id: 'login-to-upvote',
      })
      navigate('/login', { state: { from: location.pathname + location.search } })
      return
    }
    upvote.mutate({ postId: post.id, upvote: !active })
  }

  return (
    <div
      className={cn(
        'flex items-center',
        orientation === 'vertical' ? 'w-10 shrink-0 flex-col gap-0.5' : 'gap-1 rounded-full bg-surface-2 pr-3',
      )}
    >
      <button
        type="button"
        onClick={onClick}
        aria-pressed={active}
        aria-disabled={loggedOut || undefined}
        aria-label={loggedOut ? 'Log in to upvote' : active ? 'Remove upvote' : 'Upvote'}
        title={loggedOut ? 'Log in to upvote' : undefined}
        className={cn(
          'rounded-full p-1 transition-colors',
          active ? 'text-amber hover:bg-amber-muted' : 'text-muted hover:bg-amber-muted hover:text-amber',
          loggedOut && 'opacity-60',
        )}
      >
        <ArrowBigUp
          key={active ? 'on' : 'off'}
          className={cn('h-7 w-7', active && 'animate-pop fill-amber')}
          strokeWidth={1.75}
          aria-hidden
        />
      </button>
      <span
        className={cn('font-display text-sm font-bold tabular-nums', active ? 'text-amber' : 'text-ink')}
        aria-live="polite"
        aria-label={`${post.upvoteCount} upvotes`}
      >
        {compactNumber(post.upvoteCount)}
      </span>
    </div>
  )
}
