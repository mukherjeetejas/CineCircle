import { Link } from 'react-router-dom'
import type { PostMovie } from '../types'
import { cn } from '../lib/utils'
import { Poster } from './Poster'

export function MovieChip({ movie, className }: { movie: PostMovie; className?: string }) {
  return (
    <Link
      to={`/movies/${movie.tmdbId}`}
      className={cn(
        'group inline-flex max-w-full items-center gap-2 rounded-full border border-line bg-surface-2 py-1 pl-1 pr-3 text-xs font-medium',
        'transition-colors hover:border-amber/60 hover:text-amber',
        className,
      )}
    >
      <Poster url={movie.posterUrl} title={movie.title} className="h-7 w-[19px] shrink-0 rounded-full" />
      <span className="truncate">{movie.title}</span>
      {movie.releaseYear && <span className="shrink-0 text-muted group-hover:text-amber/80">{movie.releaseYear}</span>}
    </Link>
  )
}
