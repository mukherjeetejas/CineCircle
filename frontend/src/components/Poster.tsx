import { Film } from 'lucide-react'
import { useState } from 'react'
import { cn } from '../lib/utils'

interface Props {
  url: string | null | undefined
  title: string
  className?: string
}

/** Movie poster (2:3) with a graceful fallback tile. */
export function Poster({ url, title, className }: Props) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const failed = !!url && failedUrl === url
  if (!url || failed) {
    return (
      <div
        className={cn('flex aspect-[2/3] items-center justify-center rounded-lg bg-surface-2 text-muted', className)}
        role="img"
        aria-label={`${title} (no poster)`}
      >
        <Film className="h-1/3 w-1/3 max-h-10 max-w-10" aria-hidden />
      </div>
    )
  }
  return (
    <img
      src={url}
      alt={`${title} poster`}
      loading="lazy"
      onError={() => setFailedUrl(url)}
      className={cn('aspect-[2/3] rounded-lg bg-surface-2 object-cover', className)}
    />
  )
}
