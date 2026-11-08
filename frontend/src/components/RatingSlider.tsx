import type { CSSProperties } from 'react'

const VIBES: Record<number, string> = {
  1: 'Unwatchable',
  2: 'Rough',
  3: 'Meh',
  4: 'Not for me',
  5: 'Fine I guess',
  6: 'Decent',
  7: 'Good stuff',
  8: 'Great',
  9: 'Obsessed',
  10: 'Masterpiece',
}

export function RatingSlider({ value, onChange, id }: { value: number; onChange: (v: number) => void; id: string }) {
  const fill = ((value - 1) / 9) * 100
  return (
    <div>
      <div className="mb-3 flex items-end justify-between">
        <span className="text-sm text-muted">{VIBES[value]}</span>
        <span className="font-display text-4xl font-bold leading-none text-amber" aria-hidden>
          {value}
          <span className="text-lg text-muted">/10</span>
        </span>
      </div>
      <input
        id={id}
        type="range"
        min={1}
        max={10}
        step={1}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        aria-valuetext={`${value} out of 10, ${VIBES[value]}`}
        className="rating-range"
        style={{ '--fill': `${fill}%` } as CSSProperties}
      />
      <div className="mt-2 flex justify-between px-1 text-[11px] tabular-nums text-muted" aria-hidden>
        {Array.from({ length: 10 }, (_, i) => (
          <span key={i} className={i + 1 === value ? 'font-bold text-amber' : undefined}>
            {i + 1}
          </span>
        ))}
      </div>
    </div>
  )
}
