import { Lock, Search, X } from 'lucide-react'
import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMovieSearchPreview } from '../api/movies'
import { useUserSearch } from '../api/users'
import { useDebouncedValue, useDismiss } from '../lib/hooks'
import { cn } from '../lib/utils'
import { Avatar } from './Avatar'
import { Poster } from './Poster'
import { Spinner } from './Spinner'

type Option = { key: string; href: string; render: () => ReactNode }

export function GlobalSearch({ className, autoFocus, onNavigate }: { className?: string; autoFocus?: boolean; onNavigate?: () => void }) {
  const id = useId()
  const navigate = useNavigate()
  const [text, setText] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const q = useDebouncedValue(text.trim(), 300)
  const movies = useMovieSearchPreview(q)
  const users = useUserSearch(q)
  const wrapRef = useRef<HTMLDivElement>(null)
  useDismiss(open, () => setOpen(false), wrapRef)

  const movieOpts: Option[] = (movies.data?.items ?? []).slice(0, 5).map((m) => ({
    key: `m-${m.tmdbId}`,
    href: `/movies/${m.tmdbId}`,
    render: () => (
      <>
        <Poster url={m.posterUrl} title={m.title} className="h-10 w-7 shrink-0 rounded" />
        <div className="min-w-0">
          <div className="truncate text-sm font-medium">{m.title}</div>
          <div className="text-xs text-muted">
            {m.releaseYear ?? '—'}
            {m.reviewCount > 0 && ` · ${m.reviewCount} reviews`}
          </div>
        </div>
      </>
    ),
  }))
  const userOpts: Option[] = (users.data ?? []).slice(0, 5).map((u) => ({
    key: `u-${u.id}`,
    href: `/u/${u.username}`,
    render: () => (
      <>
        <Avatar url={u.avatarUrl} name={u.displayName || u.username} size="sm" />
        <div className="min-w-0">
          <div className="flex items-center gap-1 truncate text-sm font-medium">
            {u.displayName || u.username}
            {u.isPrivate && <Lock className="h-3 w-3 text-muted" aria-label="Private" />}
          </div>
          <div className="truncate text-xs text-muted">@{u.username}</div>
        </div>
      </>
    ),
  }))
  const allOpts = [...movieOpts, ...userOpts]
  const seeAllHref = `/movies/search?q=${encodeURIComponent(q)}`

  const go = (href: string) => {
    setOpen(false)
    setText('')
    setActive(-1)
    onNavigate?.()
    navigate(href)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setActive((a) => Math.min(a + 1, allOpts.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((a) => Math.max(a - 1, -1))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (active >= 0 && allOpts[active]) go(allOpts[active].href)
      else if (text.trim()) go(`/movies/search?q=${encodeURIComponent(text.trim())}`)
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  const showDropdown = open && q.length >= 1
  const loading = movies.isFetching || users.isFetching

  const renderSection = (title: string, opts: Option[], offset: number, emptyText: string) => (
    <div role="group" aria-labelledby={`${id}-${title}`}>
      <div id={`${id}-${title}`} className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wider text-muted">
        {title}
      </div>
      {opts.length === 0 ? (
        <div className="px-3 py-2 text-sm text-muted">{emptyText}</div>
      ) : (
        opts.map((o, i) => {
          const idx = offset + i
          return (
            <div
              key={o.key}
              id={`${id}-opt-${idx}`}
              role="option"
              aria-selected={idx === active}
              onMouseEnter={() => setActive(idx)}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => go(o.href)}
              className={cn(
                'flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2',
                idx === active ? 'bg-amber-muted' : 'hover:bg-surface-2',
              )}
            >
              {o.render()}
            </div>
          )
        })
      )}
    </div>
  )

  return (
    <div ref={wrapRef} className={cn('relative', className)}>
      <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
      <input
        type="search"
        role="combobox"
        aria-label="Search movies and people"
        aria-expanded={showDropdown}
        aria-controls={`${id}-list`}
        aria-autocomplete="list"
        aria-activedescendant={showDropdown && active >= 0 ? `${id}-opt-${active}` : undefined}
        placeholder="Search movies & people"
        autoComplete="off"
        autoFocus={autoFocus}
        value={text}
        onChange={(e) => {
          setText(e.target.value)
          setOpen(true)
          setActive(-1)
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKeyDown}
        className="w-full rounded-full border border-line bg-surface-2 py-2 pl-10 pr-9 text-sm text-ink placeholder:text-muted focus:border-amber focus:outline-none focus:ring-2 focus:ring-amber/30 [&::-webkit-search-cancel-button]:hidden"
      />
      {loading ? (
        <Spinner className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2" />
      ) : (
        text && (
          <button
            type="button"
            aria-label="Clear search"
            onClick={() => {
              setText('')
              setActive(-1)
            }}
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded-full p-1 text-muted hover:text-ink"
          >
            <X className="h-4 w-4" aria-hidden />
          </button>
        )
      )}

      {showDropdown && (
        <div
          id={`${id}-list`}
          role="listbox"
          aria-label="Search results"
          className="absolute left-0 right-0 z-50 mt-2 max-h-[70vh] overflow-y-auto rounded-2xl border border-line bg-surface p-1 shadow-2xl"
        >
          {renderSection(
            'Movies',
            movieOpts,
            0,
            q.length < 2 ? 'Keep typing…' : movies.isFetching ? 'Searching…' : 'No movies found',
          )}
          {renderSection('Users', userOpts, movieOpts.length, users.isFetching ? 'Searching…' : 'No people found')}
          {q.length >= 2 && (
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => go(seeAllHref)}
              className="mt-1 w-full rounded-xl px-3 py-2 text-left text-sm font-medium text-amber hover:bg-surface-2"
            >
              See all movie results for “{q}”
            </button>
          )}
        </div>
      )}
    </div>
  )
}
