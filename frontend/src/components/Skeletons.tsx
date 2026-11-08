export function PostCardSkeleton() {
  return (
    <div className="card flex gap-3 p-4" aria-hidden>
      <div className="flex w-10 flex-col items-center gap-2">
        <div className="skeleton h-7 w-7 rounded-full" />
        <div className="skeleton h-3 w-6" />
      </div>
      <div className="flex-1 space-y-3">
        <div className="flex items-center gap-2">
          <div className="skeleton h-6 w-6 rounded-full" />
          <div className="skeleton h-3 w-32" />
        </div>
        <div className="skeleton h-7 w-48 rounded-full" />
        <div className="skeleton h-5 w-3/4" />
        <div className="skeleton h-3 w-full" />
        <div className="skeleton h-3 w-5/6" />
        <div className="flex gap-3 pt-1">
          <div className="skeleton h-6 w-20 rounded-full" />
          <div className="skeleton h-6 w-16 rounded-full" />
        </div>
      </div>
    </div>
  )
}

export function PostListSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div className="space-y-3" role="status" aria-label="Loading reviews">
      {Array.from({ length: count }, (_, i) => (
        <PostCardSkeleton key={i} />
      ))}
    </div>
  )
}

export function UserRowSkeleton() {
  return (
    <div className="flex items-center gap-3 p-3" aria-hidden>
      <div className="skeleton h-10 w-10 rounded-full" />
      <div className="flex-1 space-y-2">
        <div className="skeleton h-3 w-28" />
        <div className="skeleton h-3 w-20" />
      </div>
    </div>
  )
}

export function UserListSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div role="status" aria-label="Loading people">
      {Array.from({ length: count }, (_, i) => (
        <UserRowSkeleton key={i} />
      ))}
    </div>
  )
}

export function MovieGridSkeleton({ count = 10 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3" role="status" aria-label="Loading movies">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="space-y-2" aria-hidden>
          <div className="skeleton aspect-[2/3] w-full rounded-xl" />
          <div className="skeleton h-3 w-3/4" />
          <div className="skeleton h-3 w-1/2" />
        </div>
      ))}
    </div>
  )
}

export function MiniMovieSkeleton({ count = 5 }: { count?: number }) {
  return (
    <div className="space-y-3" role="status" aria-label="Loading">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="flex items-center gap-3" aria-hidden>
          <div className="skeleton h-12 w-8 rounded-md" />
          <div className="flex-1 space-y-2">
            <div className="skeleton h-3 w-3/4" />
            <div className="skeleton h-3 w-1/3" />
          </div>
        </div>
      ))}
    </div>
  )
}

export function CommentSkeleton() {
  return (
    <div className="flex gap-3 py-3" aria-hidden>
      <div className="skeleton h-8 w-8 rounded-full" />
      <div className="flex-1 space-y-2">
        <div className="skeleton h-3 w-32" />
        <div className="skeleton h-3 w-full" />
        <div className="skeleton h-3 w-2/3" />
      </div>
    </div>
  )
}
