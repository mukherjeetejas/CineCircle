import { Star } from 'lucide-react'
import { cn } from '../lib/utils'

export function RatingBadge({ rating, className }: { rating: number; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full bg-amber px-2.5 py-1 font-display text-xs font-bold text-black',
        className,
      )}
      aria-label={`Rated ${rating} out of 10`}
      title={`${rating}/10`}
    >
      <Star className="h-3.5 w-3.5 fill-black" aria-hidden />
      {rating}
      <span className="font-medium opacity-60">/10</span>
    </span>
  )
}
