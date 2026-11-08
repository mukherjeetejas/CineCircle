import { Fragment, type ReactNode } from 'react'
import { cn } from '../lib/utils'

/**
 * "Markdown-lite" renderer that never uses innerHTML:
 * blank-line paragraphs, `> quotes`, `- lists`, **bold**, *italic*, `code`, line breaks.
 */
const INLINE = /(\*\*[^*]+\*\*|\*[^*\s][^*]*\*|_[^_\s][^_]*_|`[^`]+`)/g

function renderInline(text: string, keyBase: string): ReactNode[] {
  const out: ReactNode[] = []
  text.split('\n').forEach((line, li) => {
    if (li > 0) out.push(<br key={`${keyBase}-br-${li}`} />)
    line.split(INLINE).forEach((part, pi) => {
      const k = `${keyBase}-${li}-${pi}`
      if (!part) return
      if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
        out.push(
          <strong key={k} className="font-semibold text-ink">
            {part.slice(2, -2)}
          </strong>,
        )
      } else if (
        ((part.startsWith('*') && part.endsWith('*')) || (part.startsWith('_') && part.endsWith('_'))) &&
        part.length > 2
      ) {
        out.push(<em key={k}>{part.slice(1, -1)}</em>)
      } else if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
        out.push(
          <code key={k} className="rounded bg-surface-2 px-1 py-0.5 font-mono text-[0.9em] text-amber">
            {part.slice(1, -1)}
          </code>,
        )
      } else {
        out.push(<Fragment key={k}>{part}</Fragment>)
      }
    })
  })
  return out
}

export function RichText({ text, className }: { text: string; className?: string }) {
  const blocks = text.replace(/\r\n/g, '\n').split(/\n{2,}/)
  return (
    <div className={cn('space-y-3 break-words leading-relaxed', className)}>
      {blocks.map((block, i) => {
        const lines = block.split('\n')
        if (lines.every((l) => /^\s*[-*]\s+/.test(l))) {
          return (
            <ul key={i} className="list-disc space-y-1 pl-5 marker:text-amber">
              {lines.map((l, j) => (
                <li key={j}>{renderInline(l.replace(/^\s*[-*]\s+/, ''), `${i}-${j}`)}</li>
              ))}
            </ul>
          )
        }
        if (lines.every((l) => l.startsWith('>'))) {
          return (
            <blockquote key={i} className="border-l-2 border-amber pl-3 italic text-muted">
              {renderInline(lines.map((l) => l.replace(/^>\s?/, '')).join('\n'), `${i}`)}
            </blockquote>
          )
        }
        return <p key={i}>{renderInline(block, `${i}`)}</p>
      })}
    </div>
  )
}
