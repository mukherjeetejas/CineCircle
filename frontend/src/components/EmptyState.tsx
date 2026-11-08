import type { ReactNode } from 'react'
import { Clapperboard } from 'lucide-react'
import { cn } from '../lib/utils'

interface Props {
  title: string
  description?: ReactNode
  icon?: ReactNode
  action?: ReactNode
  className?: string
}

export function EmptyState({ title, description, icon, action, className }: Props) {
  return (
    <div className={cn('card flex flex-col items-center px-6 py-12 text-center', className)}>
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-amber-muted text-amber">
        {icon ?? <Clapperboard className="h-7 w-7" aria-hidden />}
      </div>
      <h3 className="text-lg font-semibold">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <EmptyState
      title="Well, that's a plot twist."
      description={message ?? "We couldn't load this. Check your connection and try again."}
      action={
        onRetry && (
          <button type="button" className="btn btn-outline" onClick={onRetry}>
            Try again
          </button>
        )
      }
    />
  )
}
