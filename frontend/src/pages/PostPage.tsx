import { ArrowLeft, MessageCircle, Reply, Trash2 } from 'lucide-react'
import { useMemo, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useComments, useCreateComment, useDeleteComment } from '../api/comments'
import { flattenUnique } from '../api/paging'
import { SPOILER_POLL_WINDOW_MS, usePost } from '../api/posts'
import { useAuth } from '../auth/context'
import { Avatar } from '../components/Avatar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { EmptyState, ErrorState } from '../components/EmptyState'
import { PostCard } from '../components/PostCard'
import { CommentSkeleton, PostCardSkeleton } from '../components/Skeletons'
import { Spinner } from '../components/Spinner'
import { cn, fullDate, timeAgo } from '../lib/utils'
import type { Comment } from '../types'

const MAX_COMMENT = 2000

function CommentForm({
  postId,
  parentCommentId,
  autoFocus,
  onDone,
  id,
}: {
  postId: string
  parentCommentId?: string
  autoFocus?: boolean
  onDone?: () => void
  id?: string
}) {
  const [body, setBody] = useState('')
  const create = useCreateComment(postId)
  const trimmed = body.trim()
  const reply = !!parentCommentId

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!trimmed || trimmed.length > MAX_COMMENT) return
    create.mutate(
      { body: trimmed, ...(parentCommentId ? { parentCommentId } : {}) },
      {
        onSuccess: () => {
          setBody('')
          onDone?.()
        },
      },
    )
  }

  return (
    <form onSubmit={submit} className="space-y-2">
      <label htmlFor={id ?? `reply-${parentCommentId}`} className="sr-only">
        {reply ? 'Write a reply' : 'Write a comment'}
      </label>
      <textarea
        id={id ?? `reply-${parentCommentId}`}
        value={body}
        onChange={(e) => setBody(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submit(e)
          if (e.key === 'Escape' && reply) onDone?.()
        }}
        rows={reply ? 2 : 3}
        autoFocus={autoFocus}
        maxLength={MAX_COMMENT}
        placeholder={reply ? 'Reply…' : 'Add your take…'}
        className="input resize-y"
      />
      <div className="flex items-center justify-end gap-2">
        <span className={cn('mr-auto text-xs tabular-nums', body.length > MAX_COMMENT * 0.9 ? 'text-amber' : 'text-muted')}>
          {body.length}/{MAX_COMMENT}
        </span>
        {reply && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={onDone}>
            Cancel
          </button>
        )}
        <button type="submit" className="btn btn-primary btn-sm" disabled={!trimmed || create.isPending}>
          {create.isPending && <Spinner className="h-3.5 w-3.5 text-current" />}
          {reply ? 'Reply' : 'Comment'}
        </button>
      </div>
    </form>
  )
}

function CommentItem({ comment, postId, isReply }: { comment: Comment; postId: string; isReply?: boolean }) {
  const { status } = useAuth()
  const [replying, setReplying] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const del = useDeleteComment(postId)

  return (
    <li className={cn(isReply ? 'pt-3' : 'py-4')}>
      <div className="flex gap-3">
        {comment.deleted ? (
          <span className="h-8 w-8 shrink-0 rounded-full bg-surface-2" aria-hidden />
        ) : (
          <Link to={`/u/${comment.author.username}`} className="shrink-0 rounded-full" tabIndex={-1} aria-hidden>
            <Avatar url={comment.author.avatarUrl} name={comment.author.displayName || comment.author.username} size="sm" />
          </Link>
        )}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 text-xs text-muted">
            {comment.deleted ? (
              <span>[deleted]</span>
            ) : (
              <Link to={`/u/${comment.author.username}`} className="font-semibold text-ink hover:text-amber">
                {comment.author.displayName || comment.author.username}
              </Link>
            )}
            <span aria-hidden>·</span>
            <time dateTime={comment.createdAt} title={fullDate(comment.createdAt)}>
              {timeAgo(comment.createdAt)}
            </time>
          </div>
          <p className={cn('mt-1 whitespace-pre-line break-words text-[15px]', comment.deleted && 'italic text-muted')}>
            {comment.deleted ? '[deleted]' : comment.body}
          </p>
          {!comment.deleted && (
            <div className="mt-1 flex gap-1">
              {!isReply && status === 'authenticated' && (
                <button type="button" className="btn btn-ghost btn-sm -ml-3" onClick={() => setReplying((r) => !r)} aria-expanded={replying}>
                  <Reply className="h-3.5 w-3.5" aria-hidden /> Reply
                </button>
              )}
              {comment.isMine && (
                <button
                  type="button"
                  className={cn('btn btn-ghost btn-sm hover:!bg-danger/10 hover:!text-danger', isReply && '-ml-3')}
                  onClick={() => setConfirm(true)}
                >
                  <Trash2 className="h-3.5 w-3.5" aria-hidden /> Delete
                </button>
              )}
            </div>
          )}
          {replying && (
            <div className="mt-2">
              <CommentForm postId={postId} parentCommentId={comment.id} autoFocus onDone={() => setReplying(false)} />
            </div>
          )}
          {comment.replies?.length > 0 && (
            <ul className="mt-1 border-l-2 border-line pl-4" aria-label="Replies">
              {comment.replies.map((r) => (
                <CommentItem key={r.id} comment={r} postId={postId} isReply />
              ))}
            </ul>
          )}
        </div>
      </div>
      {comment.isMine && (
        <ConfirmDialog
          open={confirm}
          title="Delete this comment?"
          description="It'll show as [deleted] in the thread."
          confirmLabel="Delete"
          danger
          busy={del.isPending}
          onCancel={() => setConfirm(false)}
          onConfirm={() => del.mutate(comment.id, { onSettled: () => setConfirm(false) })}
        />
      )}
    </li>
  )
}

function Comments({ postId }: { postId: string }) {
  const { status } = useAuth()
  const location = useLocation()
  const q = useComments(postId)
  const comments = flattenUnique(q.data)

  return (
    <section id="comments" aria-labelledby="comments-heading" className="card mt-4 p-4 sm:p-5">
      <h2 id="comments-heading" className="mb-3 flex items-center gap-2 text-lg font-bold">
        <MessageCircle className="h-5 w-5 text-amber" aria-hidden /> Comments
      </h2>
      {status === 'authenticated' ? (
        <CommentForm postId={postId} id="comment-box" />
      ) : (
        <div className="rounded-xl border border-line bg-surface-2 p-4 text-sm text-muted">
          <Link to="/login" state={{ from: location.pathname }} className="font-semibold text-amber hover:underline">
            Log in
          </Link>{' '}
          to join the conversation.
        </div>
      )}

      {q.isPending ? (
        <div className="mt-2">
          <CommentSkeleton />
          <CommentSkeleton />
        </div>
      ) : q.isError ? (
        <p className="mt-4 text-sm text-muted">
          Couldn't load comments.{' '}
          <button type="button" className="text-amber hover:underline" onClick={() => void q.refetch()}>
            Retry
          </button>
        </p>
      ) : comments.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted">No comments yet. Say something iconic.</p>
      ) : (
        <ul className="mt-2 divide-y divide-line">
          {comments.map((c) => (
            <CommentItem key={c.id} comment={c} postId={postId} />
          ))}
        </ul>
      )}
      {q.hasNextPage && (
        <div className="mt-2 flex justify-center">
          <button
            type="button"
            className="btn btn-outline btn-sm"
            onClick={() => void q.fetchNextPage()}
            disabled={q.isFetchingNextPage}
          >
            {q.isFetchingNextPage && <Spinner className="h-3.5 w-3.5" />}
            Load more comments
          </button>
        </div>
      )}
    </section>
  )
}

export function PostPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  // Poll a PENDING spoiler check for up to 30 s from when this post was opened.
  const pollPendingUntil = useMemo(() => (id ? Date.now() + SPOILER_POLL_WINDOW_MS : 0), [id])
  const post = usePost(id, { pollPendingUntil })

  return (
    <div>
      <button
        type="button"
        onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/'))}
        className="btn btn-ghost btn-sm -ml-2 mb-2"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden /> Back
      </button>
      {post.isPending ? (
        <PostCardSkeleton />
      ) : post.isError ? (
        (post.error as { status?: number }).status === 404 ? (
          <EmptyState
            title="This review has left the theatre."
            description="It was deleted, or it's from a private account you don't follow."
            action={
              <Link to="/" className="btn btn-primary">
                Back to feed
              </Link>
            }
          />
        ) : (
          <ErrorState onRetry={() => void post.refetch()} />
        )
      ) : (
        <>
          <PostCard key={post.data.id} post={post.data} detail onDeleted={() => navigate('/', { replace: true })} />
          <Comments postId={post.data.id} />
        </>
      )}
    </div>
  )
}
