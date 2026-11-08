import { Check, UserPlus, X } from 'lucide-react'
import { useFollowRequests, useRespondFollowRequest } from '../api/users'
import { useAuth } from '../auth/context'
import { EmptyState, ErrorState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
import { UserListSkeleton } from '../components/Skeletons'
import { UserRow } from '../components/UserRow'

export function RequestsPage() {
  const { user } = useAuth()
  const q = useFollowRequests(true)
  const respond = useRespondFollowRequest()

  return (
    <div>
      <PageHeader
        title="Follow requests"
        subtitle={
          user?.isPrivate
            ? 'People who want into your circle.'
            : "Your account is public, so new followers don't need approval. Older requests show here."
        }
      />
      {q.isPending ? (
        <div className="card p-2">
          <UserListSkeleton />
        </div>
      ) : q.isError ? (
        <ErrorState onRetry={() => void q.refetch()} />
      ) : q.data.length === 0 ? (
        <EmptyState
          icon={<UserPlus className="h-7 w-7" aria-hidden />}
          title="No pending requests"
          description="Inbox zero. Very cinematic."
        />
      ) : (
        <ul className="card p-2" aria-label="Pending follow requests">
          {q.data.map((u) => (
            <li key={u.id}>
              <UserRow
                user={u}
                action={
                  <div className="flex shrink-0 gap-2">
                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      onClick={() => respond.mutate({ username: u.username, action: 'accept' })}
                      aria-label={`Accept follow request from @${u.username}`}
                    >
                      <Check className="h-4 w-4" aria-hidden /> Accept
                    </button>
                    <button
                      type="button"
                      className="btn btn-outline btn-sm"
                      onClick={() => respond.mutate({ username: u.username, action: 'reject' })}
                      aria-label={`Reject follow request from @${u.username}`}
                    >
                      <X className="h-4 w-4" aria-hidden />
                      <span className="hidden sm:inline">Reject</span>
                    </button>
                  </div>
                }
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
