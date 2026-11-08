import { useState } from 'react'
import { cn } from '../lib/utils'

export const TMDB_NOTICE = 'This product uses the TMDB API but is not endorsed or certified by TMDB.'

// Official TMDB logo (from themoviedb.org/about/logos-attribution), loaded as an image, unmodified.
const TMDB_LOGO_URL =
  'https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg'

export function TmdbLogo({ className }: { className?: string }) {
  const [failed, setFailed] = useState(false)
  if (failed) {
    // Text fallback if the logo URL can't be reached.
    return (
      <span
        className={cn(
          'inline-block bg-gradient-to-r from-[#90cea1] to-[#01b4e4] bg-clip-text font-display font-bold text-transparent',
          className,
        )}
      >
        TMDB
      </span>
    )
  }
  return (
    <img
      src={TMDB_LOGO_URL}
      alt="The Movie Database (TMDB) logo"
      onError={() => setFailed(true)}
      className={className}
    />
  )
}

/** Slim one-line footer used in the right rail and on mobile. */
export function TmdbFooterLine({ className }: { className?: string }) {
  return (
    <p className={cn('flex items-center gap-2 text-[11px] leading-snug text-muted', className)}>
      <a href="https://www.themoviedb.org" target="_blank" rel="noreferrer" className="shrink-0 rounded">
        <TmdbLogo className="h-3 w-auto" />
      </a>
      <span>{TMDB_NOTICE}</span>
    </p>
  )
}
