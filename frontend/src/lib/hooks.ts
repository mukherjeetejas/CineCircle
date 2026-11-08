import { useEffect, useRef, useState } from 'react'

export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = window.setTimeout(() => setDebounced(value), delayMs)
    return () => window.clearTimeout(t)
  }, [value, delayMs])
  return debounced
}

/**
 * Minimal infinite scroll: returns a ref for a sentinel element; when it scrolls into view
 * (with a 600px head start) and `enabled`, `onLoadMore` is called.
 */
export function useInfiniteScroll<T extends Element>(onLoadMore: () => void, enabled: boolean) {
  const ref = useRef<T | null>(null)
  const cb = useRef(onLoadMore)
  useEffect(() => {
    cb.current = onLoadMore
  }, [onLoadMore])

  useEffect(() => {
    const el = ref.current
    if (!el || !enabled) return
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) cb.current()
      },
      { rootMargin: '600px 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [enabled])

  return ref
}

/** Close a popover on outside click / Escape. */
export function useDismiss(open: boolean, onClose: () => void, ref: React.RefObject<HTMLElement | null>) {
  const cb = useRef(onClose)
  useEffect(() => {
    cb.current = onClose
  }, [onClose])
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) cb.current()
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') cb.current()
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open, ref])
}
