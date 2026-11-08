import { Lock } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { UserSummary } from '../types'
import { Avatar } from './Avatar'

export function UserRow({ user, action }: { user: UserSummary; action?: ReactNode }) {
  return (
    <div className="flex items-center gap-3 rounded-xl p-3 transition-colors hover:bg-surface-2">
      <Link to={`/u/${user.username}`} className="flex min-w-0 flex-1 items-center gap-3 rounded-lg">
        <Avatar url={user.avatarUrl} name={user.displayName || user.username} />
        <div className="min-w-0">
          <div className="flex items-center gap-1 truncate font-medium">
            <span className="truncate">{user.displayName || user.username}</span>
            {user.isPrivate && <Lock className="h-3 w-3 shrink-0 text-muted" aria-label="Private account" />}
          </div>
          <div className="truncate text-sm text-muted">@{user.username}</div>
        </div>
      </Link>
      {action}
    </div>
  )
}
