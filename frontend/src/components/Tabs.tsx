import { useRef, type KeyboardEvent } from 'react'
import { cn } from '../lib/utils'

export interface TabItem<T extends string> {
  value: T
  label: string
  count?: number
}

interface Props<T extends string> {
  items: TabItem<T>[]
  value: T
  onChange: (v: T) => void
  label: string
  idPrefix?: string
  className?: string
}

/** WAI-ARIA tablist with arrow-key navigation. Panels should use id `${idPrefix}-panel`. */
export function Tabs<T extends string>({ items, value, onChange, label, idPrefix = 'tabs', className }: Props<T>) {
  const refs = useRef<Array<HTMLButtonElement | null>>([])

  const onKeyDown = (e: KeyboardEvent, i: number) => {
    let next = -1
    if (e.key === 'ArrowRight') next = (i + 1) % items.length
    if (e.key === 'ArrowLeft') next = (i - 1 + items.length) % items.length
    if (e.key === 'Home') next = 0
    if (e.key === 'End') next = items.length - 1
    if (next >= 0) {
      e.preventDefault()
      refs.current[next]?.focus()
      onChange(items[next].value)
    }
  }

  return (
    <div
      role="tablist"
      aria-label={label}
      className={cn('scrollbar-none flex gap-1 overflow-x-auto rounded-full border border-line bg-surface p-1', className)}
    >
      {items.map((it, i) => {
        const selected = it.value === value
        return (
          <button
            key={it.value}
            ref={(el) => {
              refs.current[i] = el
            }}
            role="tab"
            id={`${idPrefix}-tab-${it.value}`}
            aria-selected={selected}
            aria-controls={`${idPrefix}-panel`}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(it.value)}
            onKeyDown={(e) => onKeyDown(e, i)}
            className={cn(
              'flex-1 whitespace-nowrap rounded-full px-4 py-1.5 text-sm font-semibold transition-colors',
              selected ? 'bg-amber text-black' : 'text-muted hover:bg-surface-2 hover:text-ink',
            )}
          >
            {it.label}
            {it.count !== undefined && (
              <span className={cn('ml-1.5 tabular-nums', selected ? 'text-black/60' : 'text-muted')}>{it.count}</span>
            )}
          </button>
        )
      })}
    </div>
  )
}
