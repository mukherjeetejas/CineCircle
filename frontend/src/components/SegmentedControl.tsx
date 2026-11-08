import type { ReactNode, KeyboardEvent } from 'react'
import { useRef } from 'react'
import { cn } from '../lib/utils'

export interface Segment<T extends string> {
  value: T
  label: string
  icon?: ReactNode
}

interface Props<T extends string> {
  segments: Segment<T>[]
  value: T
  onChange: (v: T) => void
  label: string
}

/** Radio-group semantics (arrow keys move + select), styled as a pill switch. */
export function SegmentedControl<T extends string>({ segments, value, onChange, label }: Props<T>) {
  const refs = useRef<Array<HTMLButtonElement | null>>([])
  const onKeyDown = (e: KeyboardEvent, i: number) => {
    let next = -1
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') next = (i + 1) % segments.length
    if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') next = (i - 1 + segments.length) % segments.length
    if (next >= 0) {
      e.preventDefault()
      refs.current[next]?.focus()
      onChange(segments[next].value)
    }
  }

  return (
    <div role="radiogroup" aria-label={label} className="grid grid-cols-3 gap-1 rounded-2xl border border-line bg-surface-2 p-1">
      {segments.map((s, i) => {
        const checked = s.value === value
        return (
          <button
            key={s.value}
            ref={(el) => {
              refs.current[i] = el
            }}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={checked ? 0 : -1}
            onClick={() => onChange(s.value)}
            onKeyDown={(e) => onKeyDown(e, i)}
            className={cn(
              'flex items-center justify-center gap-1.5 rounded-xl px-2 py-2 text-xs font-semibold transition-colors sm:text-sm',
              checked ? 'bg-amber text-black shadow-glow-lg' : 'text-muted hover:bg-surface hover:text-ink',
            )}
          >
            {s.icon}
            <span className="truncate">{s.label}</span>
          </button>
        )
      })}
    </div>
  )
}
