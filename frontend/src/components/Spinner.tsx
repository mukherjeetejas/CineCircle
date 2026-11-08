import { Loader2 } from 'lucide-react'
import { cn } from '../lib/utils'

export function Spinner({ className }: { className?: string }) {
  return <Loader2 aria-hidden className={cn('h-6 w-6 animate-spin text-amber', className)} />
}
