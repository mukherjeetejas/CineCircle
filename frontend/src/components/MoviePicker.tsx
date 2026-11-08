import { Search, X } from 'lucide-react'
import { useId, useRef, useState, type KeyboardEvent } from 'react'
import { useMovieSearchPreview } from '../api/movies'
import { useDebouncedValue, useDismiss } from '../lib/hooks'
import { cn, formatScore } from '../lib/utils'
import type { MovieSummary } from '../types'
import { Poster } from './Poster'
import { Spinner } from './Spinner'

interface Props {
  value: MovieSummary | null
  onChange: (movie: MovieSummary | null) => void
  error?: string
  disabled?: boolean
}

/**
 * TMDB-backed combobox. The user MUST pick a result; free text is never submitted.
 */
export function MoviePicker({ value, onChange, error, disabled }: Props) {
  const id = useId()
  const listId = `${id}-list`
  const [text, setText] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const debounced = useDebouncedValue(text, 300)
  const { data, isFetching, isError } = useMovieSearchPreview(debounced)
  const results = (data?.items ?? []).slice(0, 8)
  const wrapRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  useDismiss(open, () => setOpen(false), wrapRef)

  const pick = (m: MovieSummary) => {
    onChange(m)
    setText('')
    setOpen(false)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setActive((a) => Math.min(a + 1, results.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((a) => Math.max(a - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault() // never submit the form with free text
      if (open && results[active]) pick(results[active])
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  if (value) {
    return (
      <div>
        <div className="flex items-center gap-3 rounded-2xl border border-amber/50 bg-amber-muted/40 p-2 pr-3 shadow-glow">
          <Poster url={value.posterUrl} title={value.title} className="h-16 w-11 shrink-0 rounded-md" />
          <div className="min-w-0 flex-1">
            <div className="truncate font-display font-semibold">{value.title}</div>
            <div className="text-sm text-muted">
              {value.releaseYear ?? 'Year unknown'}
              {value.reviewCount > 0 && ` · ${value.reviewCount} reviews here`}
            </div>
          </div>
          {!disabled && (
            <button
              type="button"
              className="rounded-full p-2 text-muted hover:bg-surface-2 hover:text-ink"
              aria-label={`Remove ${value.title}, pick another movie`}
              onClick={() => {
                onChange(null)
                requestAnimationFrame(() => inputRef.current?.focus())
              }}
            >
              <X className="h-4 w-4" aria-hidden />
            </button>
          )}
        </div>
      </div>
    )
  }

  const showList = open && debounced.trim().length >= 2
  return (
    <div ref={wrapRef} className="relative">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
        <input
          ref={inputRef}
          id={`${id}-input`}
          type="text"
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={showList && results[active] ? `${id}-opt-${active}` : undefined}
          aria-invalid={!!error}
          aria-label="Search for a movie"
          placeholder="Search TMDB for a movie…"
          autoComplete="off"
          className={cn('input pl-10', error && 'border-danger')}
          value={text}
          disabled={disabled}
          onChange={(e) => {
            setText(e.target.value)
            setOpen(true)
            setActive(0)
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
        />
        {isFetching && <Spinner className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2" />}
      </div>
      {showList && (
        <ul
          id={listId}
          role="listbox"
          aria-label="Movie results"
          className="absolute z-30 mt-2 max-h-96 w-full overflow-y-auto rounded-2xl border border-line bg-surface p-1 shadow-2xl"
        >
          {results.map((m, i) => (
            <li
              key={m.tmdbId}
              id={`${id}-opt-${i}`}
              role="option"
              aria-selected={i === active}
              onMouseEnter={() => setActive(i)}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => pick(m)}
              className={cn(
                'flex cursor-pointer items-center gap-3 rounded-xl p-2',
                i === active ? 'bg-amber-muted text-ink' : 'hover:bg-surface-2',
              )}
            >
              <Poster url={m.posterUrl} title={m.title} className="h-12 w-8 shrink-0 rounded" />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium">{m.title}</div>
                <div className="text-xs text-muted">
                  {m.releaseYear ?? '—'}
                  {m.reviewCount > 0 && ` · ${m.reviewCount} reviews · avg ${formatScore(m.avgRating)}`}
                </div>
              </div>
            </li>
          ))}
          {!isFetching && results.length === 0 && (
            <li className="p-3 text-sm text-muted" role="option" aria-selected={false} aria-disabled>
              {isError ? "Search is unavailable right now." : `No movies match "${debounced}". Check the spelling?`}
            </li>
          )}
        </ul>
      )}
      {error && <p className="field-error">{error}</p>}
    </div>
  )
}
