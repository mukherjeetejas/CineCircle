import { useState } from 'react'
import { cn } from '../lib/utils'

const SIZES = {
  xs: 'h-6 w-6 text-[10px]',
  sm: 'h-8 w-8 text-xs',
  md: 'h-10 w-10 text-sm',
  lg: 'h-16 w-16 text-xl',
  xl: 'h-24 w-24 text-3xl',
} as const

// Deterministic warm gradient per user so initials avatars feel distinct.
const GRADIENTS = [
  'from-amber to-orange-600',
  'from-rose-500 to-amber',
  'from-fuchsia-500 to-orange-400',
  'from-yellow-300 to-amber',
  'from-orange-500 to-red-500',
]

function hash(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return Math.abs(h)
}

interface Props {
  url: string | null | undefined
  name: string
  size?: keyof typeof SIZES
  className?: string
}

export function Avatar({ url, name, size = 'md', className }: Props) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const failed = !!url && failedUrl === url
  const setFailed = () => setFailedUrl(url ?? null)
  const initials = name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? '')
    .join('')

  if (url && !failed) {
    return (
      <img
        src={url}
        alt=""
        onError={setFailed}
        className={cn('shrink-0 rounded-full object-cover ring-1 ring-line', SIZES[size], className)}
      />
    )
  }
  return (
    <span
      aria-hidden
      className={cn(
        'inline-flex shrink-0 select-none items-center justify-center rounded-full bg-gradient-to-br font-display font-bold text-black',
        GRADIENTS[hash(name) % GRADIENTS.length],
        SIZES[size],
        className,
      )}
    >
      {initials || '?'}
    </span>
  )
}
