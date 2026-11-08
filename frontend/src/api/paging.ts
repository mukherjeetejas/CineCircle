import type { InfiniteData } from '@tanstack/react-query'
import type { Page } from '../types'

export const firstPage = 0

export function nextPageParam<T>(last: Page<T>): number | undefined {
  return last.hasNext ? last.page + 1 : undefined
}

/** Flatten infinite pages and drop duplicate ids (feed backfill can repeat across pages). */
export function flattenUnique<T extends { id: string }>(data: InfiniteData<Page<T>> | undefined): T[] {
  if (!data) return []
  const seen = new Set<string>()
  const out: T[] = []
  for (const p of data.pages) {
    for (const item of p.items) {
      if (seen.has(item.id)) continue
      seen.add(item.id)
      out.push(item)
    }
  }
  return out
}

export function isInfinitePage(data: unknown): data is InfiniteData<Page<unknown>> {
  return (
    typeof data === 'object' &&
    data !== null &&
    Array.isArray((data as InfiniteData<unknown>).pages) &&
    (data as InfiniteData<Page<unknown>>).pages.every((p) => p && Array.isArray(p.items))
  )
}
