import { Lock, MessageCircle, Share2, Trash2 } from 'lucide-react'
import { useState } from 'react'
import toast from 'react-hot-toast'
import { Link, useNavigate } from 'react-router-dom'
import { useDeletePost } from '../api/posts'
import { compactNumber, cn, fullDate, postUrl, timeAgo } from '../lib/utils'
import type { PostDetail, PostSummary } from '../types'
import { Avatar } from './Avatar'
import { ConfirmDialog } from './ConfirmDialog'
import { MovieChip } from './MovieChip'
import { RatingBadge } from './RatingBadge'
import { RichText } from './RichText'
import { SpoilerGuard } from './SpoilerGuard'
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

  const share = async () => {
    const url = postUrl(post.id)
    try {
      if (navigator.share && window.matchMedia('(pointer: coarse)').matches) {
        await navigator.share({ title: post.title, url })
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
          {post.containsSpoilers && (
            <span className="rounded-full border border-danger/40 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-danger">
              Spoilers
            </span>
          )}
        </div>

        {/* title */}
        {detail ? (
          <h1 id={`post-title-${post.id}`} className="mt-3 text-2xl font-bold leading-tight sm:text-3xl">
            {post.title}
          </h1>
        ) : (
          <h2 id={`post-title-${post.id}`} className="mt-3 text-lg font-semibold leading-snug">
            <Link to={href} className="rounded hover:text-amber">
              {post.title}
            </Link>
          </h2>
        )}

        {/* body + image (blurred together when spoilers) */}
        <SpoilerGuard active={post.containsSpoilers}>
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
          {post.imageUrl && (
            <div className="mt-3 overflow-hidden rounded-xl border border-line bg-surface-2">
              <img
                src={post.imageUrl}
                alt={`Image for review of ${post.movie.title}`}
                loading="lazy"
                className={cn('w-full object-cover', detail ? 'max-h-[640px] object-contain' : 'max-h-[480px]')}
              />
            </div>
          )}
        </SpoilerGuard>

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
