import { Link } from 'react-router-dom'
import { cn } from '../lib/utils'

export function Logo({ className, asLink = true }: { className?: string; asLink?: boolean }) {
  const mark = (
    <span className={cn('font-display text-xl font-bold leading-none tracking-tight text-ink', className)}>
      cinecircle
      <span className="ml-0.5 inline-block h-2 w-2 rounded-full bg-amber align-baseline shadow-[0_0_10px_#FFB000]" aria-hidden />
    </span>
  )
  if (!asLink) return mark
  return (
    <Link to="/" aria-label="cinecircle home" className="rounded-lg px-1 py-1">
      {mark}
    </Link>
  )
}
