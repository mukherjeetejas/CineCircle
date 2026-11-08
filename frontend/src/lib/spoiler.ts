import type { PostSummary, SpoilerInfo } from '../types'

export interface SpoilerView {
  /** Blur body + custom image until revealed: `flagged || aiStatus === 'PENDING'`. */
  blurred: boolean
  /** AI check still running. */
  pending: boolean
  /** Author ticked "contains spoilers". */
  byAuthor: boolean
  /** AI decided SPOILER. */
  byAi: boolean
  /** Spoiler-free reason from the AI (only when byAi). */
  aiReason: string | null
  /** Replace the title while blurred. */
  titleHidden: boolean
}

/** Derive what the UI should do from a post's `spoiler` object (FEATURES-V2.md, Feature 3). */
export function spoilerView(post: Pick<PostSummary, 'spoiler' | 'containsSpoilers'>): SpoilerView {
  // Defensive fallback for a payload without `spoiler` (e.g. an older backend): v1 behaviour.
  const s: SpoilerInfo = post.spoiler ?? {
    flagged: post.containsSpoilers,
    source: post.containsSpoilers ? 'AUTHOR' : 'NONE',
    aiStatus: 'UNCHECKED',
    aiReason: null,
    titleHidden: false,
  }
  const pending = s.aiStatus === 'PENDING'
  const byAuthor = s.source === 'AUTHOR' || s.source === 'BOTH'
  const byAi = s.source === 'AI' || s.source === 'BOTH'
  return {
    blurred: s.flagged || pending,
    pending,
    byAuthor,
    byAi,
    aiReason: byAi ? s.aiReason : null,
    titleHidden: s.titleHidden,
  }
}

/**
 * Only CUSTOM (uploaded) images are blurred; TMDB posters are not. `PostSummary` exposes only
 * `imageUrl`, so the image type is inferred from the URL: TMDB posters are served from image.tmdb.org.
 */
export function isTmdbImage(url: string | null): boolean {
  return !!url && url.startsWith('https://image.tmdb.org/')
}
