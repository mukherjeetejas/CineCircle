import { Lock, MessageCircle, Share2, Trash2 } from 'lucide-react'
import { useState } from 'react'
import toast from 'react-hot-toast'
import { Link, useNavigate } from 'react-router-dom'
import { useDeletePost } from '../api/posts'
import { isTmdbImage, spoilerView } from '../lib/spoiler'
import { compactNumber, cn, fullDate, postUrl, timeAgo } from '../lib/utils'
import type { PostDetail, PostSummary } from '../types'
import { Avatar } from './Avatar'
import { ConfirmDialog } from './ConfirmDialog'
import { MovieChip } from './MovieChip'
import { RatingBadge } from './RatingBadge'
import { RichText } from './RichText'
import { SpoilerGuard } from './SpoilerGuard'
import { SpoilerTags, SpoilerToggle } from './SpoilerTags'
import { UpvoteButton } from './UpvoteButton'

interface Props {
  post: PostSummary | PostDetail
  /** Full post page: shows the whole body, no title link. */
  detail?: boolean
  /** Called after a successful delete (e.g. navigate away from detail page). */
  onDeleted?: () => void
}

export function PostCard({ post, detail = false, onDeleted }: Props) {
  const [confirmOpen, setConfirmOpen] = useState(false)
  const del = useDeletePost()
  const navigate = useNavigate()
  const href = `/posts/${post.id}`
  const body = detail && 'body' in post ? post.body : post.bodyPreview
  // Backend trims the preview and appends "…" when the body was cut.
  const truncated = !detail && post.bodyPreview.endsWith('…')

  // Spoilers: blur body + CUSTOM image (never TMDB posters) until this reader reveals them for this visit.
  const spoiler = spoilerView(post)
  const [revealed, setRevealed] = useState(false)
  const hidden = spoiler.blurred && !revealed
  const titleText = hidden && spoiler.titleHidden ? 'Title hidden – contains spoilers' : post.title
  const blurImage = !!post.imageUrl && !isTmdbImage(post.imageUrl)
  const bodyId = `post-body-${post.id}`
  const imageId = `post-image-${post.id}`
  const titleId = `post-title-${post.id}`
  const controls = [spoiler.titleHidden && titleId, bodyId, blurImage && imageId].filter(Boolean).join(' ')

  const share = async () => {
    const url = postUrl(post.id)
    try {
      if (navigator.share && window.matchMedia('(pointer: coarse)').matches) {
        await navigator.share({ title: spoiler.titleHidden ? `A review of ${post.movie.title}` : post.title, url })
        return
      }
      await navigator.clipboard.writeText(url)
      toast.success('Link copied — go spread the word')
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      toast.error("Couldn't copy the link")
    }
  }

  return (
    <article
      className={cn('card flex gap-2 p-3 sm:gap-3 sm:p-4', !detail && 'card-hover')}
      aria-labelledby={`post-title-${post.id}`}
    >
      <UpvoteButton post={post} />

      <div className="min-w-0 flex-1">
        {/* author line */}
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted">
          <Link
            to={`/u/${post.author.username}`}
            className="inline-flex items-center gap-1.5 rounded-full font-medium text-ink hover:text-amber"
          >
            <Avatar url={post.author.avatarUrl} name={post.author.displayName || post.author.username} size="xs" />
            <span>{post.author.displayName || post.author.username}</span>
            <span className="text-muted">@{post.author.username}</span>
          </Link>
          {post.author.isPrivate && <Lock className="h-3 w-3" aria-label="Private account" />}
          <span aria-hidden>·</span>
          <time dateTime={post.createdAt} title={fullDate(post.createdAt)}>
            {timeAgo(post.createdAt)}
          </time>
        </div>

        {/* movie + rating */}
        <div className="mt-2.5 flex flex-wrap items-center gap-2">
          <MovieChip movie={post.movie} />
          <RatingBadge rating={post.rating} />
          <SpoilerTags view={spoiler} />
          {spoiler.blurred && (
            <SpoilerToggle revealed={revealed} onToggle={() => setRevealed((r) => !r)} controls={controls} />
          )}
        </div>

        {/* title */}
        {detail ? (
          <h1
            id={titleId}
            className={cn('mt-3 text-2xl font-bold leading-tight sm:text-3xl', titleText !== post.title && 'italic text-muted')}
          >
            {titleText}
          </h1>
        ) : (
          <h2 id={titleId} className="mt-3 text-lg font-semibold leading-snug">
            <Link to={href} className={cn('rounded hover:text-amber', titleText !== post.title && 'italic text-muted')}>
              {titleText}
            </Link>
          </h2>
        )}

        {/* body (blurred when spoilers / pending check) */}
        <SpoilerGuard hidden={hidden} onReveal={() => setRevealed(true)} id={bodyId}>
          {body && (
            <div className={cn('mt-2 text-[15px] text-ink/85', !detail && 'line-clamp-6')}>
              {detail ? <RichText text={body} /> : <p className="whitespace-pre-line break-words">{body}</p>}
            </div>
          )}
          {truncated && (
            <Link to={href} className="mt-1 inline-block text-sm font-medium text-amber hover:underline">
              Read more
            </Link>
          )}
        </SpoilerGuard>

        {/* image: an uploaded image is blurred too; TMDB posters never are */}
        {post.imageUrl && (
          <SpoilerGuard hidden={hidden && blurImage} onReveal={() => setRevealed(true)} id={imageId}>
            <div className="mt-3 overflow-hidden rounded-xl border border-line bg-surface-2">
              <img
                src={post.imageUrl}
                alt={`Image for review of ${post.movie.title}`}
                loading="lazy"
                className={cn('w-full object-cover', detail ? 'max-h-[640px] object-contain' : 'max-h-[480px]')}
              />
            </div>
          </SpoilerGuard>
        )}

        {/* footer */}
        <div className="mt-3 flex flex-wrap items-center gap-1 text-sm">
          <Link to={detail ? '#comments' : `${href}#comments`} className="btn btn-ghost btn-sm" onClick={(e) => {
            if (detail) {
              e.preventDefault()
              document.getElementById('comment-box')?.focus()
            }
          }}>
            <MessageCircle className="h-4 w-4" aria-hidden />
            {compactNumber(post.commentCount)} {post.commentCount === 1 ? 'comment' : 'comments'}
          </Link>
          <button type="button" className="btn btn-ghost btn-sm" onClick={share}>
            <Share2 className="h-4 w-4" aria-hidden />
            Share
          </button>
          {post.isMine && (
            <button
              type="button"
              className="btn btn-ghost btn-sm hover:!bg-danger/10 hover:!text-danger"
              onClick={() => setConfirmOpen(true)}
            >
              <Trash2 className="h-4 w-4" aria-hidden />
              Delete
            </button>
          )}
        </div>
      </div>

      {post.isMine && (
        <ConfirmDialog
          open={confirmOpen}
          title="Delete this review?"
          description="Its upvotes, comments and image go with it. This can't be undone."
          confirmLabel="Delete"
          danger
          busy={del.isPending}
          onCancel={() => setConfirmOpen(false)}
          onConfirm={() =>
            del.mutate(post, {
              onSuccess: () => {
                setConfirmOpen(false)
                if (onDeleted) onDeleted()
                else if (detail) navigate('/')
              },
            })
          }
        />
      )}
    </article>
  )
}
