import { Check, Clock, UserPlus } from 'lucide-react'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useFollowMutation } from '../api/users'
import { useAuth } from '../auth/context'
import { cn } from '../lib/utils'
import type { UserProfile } from '../types'
import { ConfirmDialog } from './ConfirmDialog'

export function FollowButton({
  profile,
  className,
  size = 'md',
}: {
  profile: UserProfile
  className?: string
  size?: 'sm' | 'md'
}) {
  const sizeCls = size === 'sm' ? 'btn-sm' : ''
  const { status } = useAuth()
  const follow = useFollowMutation()
  const navigate = useNavigate()
  const location = useLocation()
  const [hover, setHover] = useState(false)
  const [confirmUnfollow, setConfirmUnfollow] = useState(false)

  if (profile.followStatus === 'SELF') {
    return (
      <Link to="/settings" className={cn('btn btn-outline', sizeCls, className)}>
        Edit profile
      </Link>
    )
  }

  const vars = { username: profile.username, isPrivate: profile.isPrivate, from: profile.followStatus }

  if (status !== 'authenticated') {
    return (
      <button
        type="button"
        className={cn('btn btn-primary', sizeCls, className)}
        onClick={() => navigate('/login', { state: { from: location.pathname } })}
      >
        <UserPlus className="h-4 w-4" aria-hidden /> Follow
      </button>
    )
  }

  if (profile.followStatus === 'NONE') {
    return (
      <button
        type="button"
        className={cn('btn btn-primary', sizeCls, className)}
        onClick={() => follow.mutate({ ...vars, action: 'follow' })}
        disabled={follow.isPending}
      >
        <UserPlus className="h-4 w-4" aria-hidden /> Follow
      </button>
    )
  }

  const pending = profile.followStatus === 'PENDING'
  return (
    <>
      <button
        type="button"
        className={cn(
          'btn border', size === 'sm' ? 'btn-sm min-w-[96px]' : 'min-w-[120px]',
          hover ? 'border-danger text-danger' : 'border-line bg-surface-2 text-ink',
          className,
        )}
        onMouseEnter={() => setHover(true)}
        onMouseLeave={() => setHover(false)}
        onFocus={() => setHover(true)}
        onBlur={() => setHover(false)}
        onClick={() => setConfirmUnfollow(true)}
        disabled={follow.isPending}
        aria-label={pending ? `Cancel follow request to @${profile.username}` : `Unfollow @${profile.username}`}
      >
        {pending ? <Clock className="h-4 w-4" aria-hidden /> : <Check className="h-4 w-4" aria-hidden />}
        {hover ? (pending ? 'Cancel' : 'Unfollow') : pending ? 'Requested' : 'Following'}
      </button>
      <ConfirmDialog
        open={confirmUnfollow}
        title={pending ? `Cancel your request to @${profile.username}?` : `Unfollow @${profile.username}?`}
        description={
          pending
            ? "They won't see your request anymore. You can always ask again."
            : profile.isPrivate
            ? "Their account is private — you'll need to request again to see their reviews."
            : 'Their reviews will stop showing up in your feed.'
        }
        confirmLabel={pending ? "Cancel request" : "Unfollow"}
        danger
        onCancel={() => setConfirmUnfollow(false)}
        onConfirm={() => {
          setConfirmUnfollow(false)
          follow.mutate({ ...vars, action: 'unfollow' })
        }}
      />
    </>
  )
}
