import { Ban, Bot, Clapperboard, EyeOff, ImageIcon, Upload } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import toast from 'react-hot-toast'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useMovie } from '../api/movies'
import { useCreatePost } from '../api/posts'
import { ImageDropzone } from '../components/ImageDropzone'
import { MoviePicker } from '../components/MoviePicker'
import { PageHeader } from '../components/PageHeader'
import { Poster } from '../components/Poster'
import { RatingSlider } from '../components/RatingSlider'
import { SegmentedControl } from '../components/SegmentedControl'
import { Spinner } from '../components/Spinner'
import { cn, validateImage } from '../lib/utils'
import type { ImageType, MovieSummary } from '../types'

const MAX_TITLE = 120
const MAX_BODY = 5000

type Errors = Partial<Record<'movie' | 'title' | 'body' | 'rating' | 'image' | 'form', string>>

function Counter({ value, max }: { value: number; max: number }) {
  return (
    <span className={cn('text-xs tabular-nums', value > max ? 'text-danger' : value > max * 0.9 ? 'text-amber' : 'text-muted')}>
      {value}/{max}
    </span>
  )
}

export function SubmitPage() {
  const [params] = useSearchParams()
  const prefillId = Number(params.get('tmdbId'))
  const prefill = useMovie(Number.isFinite(prefillId) && prefillId > 0 ? prefillId : undefined)
  const navigate = useNavigate()
  const create = useCreatePost()

  // `undefined` = untouched (falls back to prefill), `null` = user cleared it.
  const [picked, setPicked] = useState<MovieSummary | null | undefined>(undefined)
  const movie: MovieSummary | null = picked === undefined ? (prefill.data ?? null) : picked

  const [rating, setRating] = useState(7)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [spoilers, setSpoilers] = useState(false)
  const [imageType, setImageType] = useState<ImageType>('NONE')
  const [file, setFile] = useState<File | null>(null)
  const [errors, setErrors] = useState<Errors>({})

  const validate = (): Errors => {
    const e: Errors = {}
    if (!movie) e.movie = 'Pick a movie from the search results.'
    if (!title.trim()) e.title = 'Give your review a title.'
    else if (title.trim().length > MAX_TITLE) e.title = `Keep it under ${MAX_TITLE} characters.`
    if (!body.trim()) e.body = 'Say a little something about it.'
    else if (body.trim().length > MAX_BODY) e.body = `Reviews must be 1–${MAX_BODY} characters.`
    if (rating < 1 || rating > 10) e.rating = 'Rating must be 1–10.'
    if (imageType === 'CUSTOM') {
      if (!file) e.image = 'Choose an image to upload, or pick another image option.'
      else {
        const err = validateImage(file)
        if (err) e.image = err
      }
    }
    if (imageType === 'TMDB_POSTER' && movie && !movie.posterUrl) e.image = "This movie doesn't have a poster on TMDB."
    return e
  }

  const submit = (ev: FormEvent) => {
    ev.preventDefault()
    const e = validate()
    setErrors(e)
    if (Object.keys(e).length > 0 || !movie) {
      const first = document.querySelector<HTMLElement>('[aria-invalid="true"]')
      first?.focus()
      return
    }
    create.mutate(
      {
        post: {
          tmdbId: movie.tmdbId,
          title: title.trim(),
          body: body.trim(),
          rating,
          containsSpoilers: spoilers,
          imageType,
        },
        image: imageType === 'CUSTOM' ? file : null,
      },
      {
        onSuccess: (post) => {
          toast.success('Review posted. Main character energy.')
          // AI flagged it although the author didn't tick the toggle (source AI, not BOTH).
          if (!spoilers && post.spoiler?.aiStatus === 'SPOILER') {
            toast('Heads up: our AI spotted spoilers, so your review will be blurred for readers.', {
              icon: <Bot className="h-5 w-5 shrink-0 text-amber" aria-hidden />,
              duration: 7000,
            })
          }
          navigate(`/posts/${post.id}`, { replace: true })
        },
        onError: (err) => {
          if (err instanceof ApiError) {
            const fe = err.fieldErrors
            const mapped: Errors = {
              title: fe.title ?? fe['post.title'],
              body: fe.body ?? fe['post.body'],
              rating: fe.rating ?? fe['post.rating'],
              image: fe.image ?? fe.imageType,
              movie: fe.tmdbId ?? (err.code === 'MOVIE_NOT_FOUND' ? err.message : undefined),
            }
            const hasField = Object.values(mapped).some(Boolean)
            setErrors({ ...mapped, form: hasField ? undefined : err.message })
            if (!err.toasted) toast.error(err.message)
          } else {
            setErrors({ form: 'Something went wrong. Try again.' })
          }
        },
      },
    )
  }

  const busy = create.isPending

  return (
    <div>
      <PageHeader title="Write a review" subtitle="Pick a movie, rate it, spill your thoughts." />
      <form onSubmit={submit} noValidate className="space-y-4">
        {/* 1. movie */}
        <section className="card p-4 sm:p-5" aria-labelledby="step-movie">
          <h2 id="step-movie" className="mb-3 flex items-center gap-2 font-semibold">
            <Clapperboard className="h-4 w-4 text-amber" aria-hidden /> Movie
          </h2>
          {prefill.isLoading && picked === undefined ? (
            <div className="flex items-center gap-2 text-sm text-muted">
              <Spinner className="h-4 w-4" /> Loading movie…
            </div>
          ) : (
            <MoviePicker
              value={movie}
              onChange={(m) => {
                setPicked(m)
                setErrors((e) => ({ ...e, movie: undefined }))
              }}
              error={errors.movie}
              disabled={busy}
            />
          )}
        </section>

        {/* 2. rating + text */}
        <section className="card space-y-5 p-4 sm:p-5" aria-label="Your review">
          <div>
            <label htmlFor="rating" className="label">
              Rating
            </label>
            <RatingSlider id="rating" value={rating} onChange={setRating} />
            {errors.rating && <p className="field-error">{errors.rating}</p>}
          </div>

          <div>
            <div className="flex items-baseline justify-between">
              <label htmlFor="title" className="label">
                Title
              </label>
              <Counter value={title.length} max={MAX_TITLE} />
            </div>
            <input
              id="title"
              className={cn('input', errors.title && 'border-danger')}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={MAX_TITLE + 20}
              placeholder="Sum it up in one line"
              aria-invalid={!!errors.title}
              aria-describedby={errors.title ? 'title-error' : undefined}
              disabled={busy}
            />
            {errors.title && (
              <p id="title-error" className="field-error">
                {errors.title}
              </p>
            )}
          </div>

          <div>
            <div className="flex items-baseline justify-between">
              <label htmlFor="body" className="label">
                Review
              </label>
              <Counter value={body.length} max={MAX_BODY} />
            </div>
            <textarea
              id="body"
              className={cn('input min-h-[180px] resize-y leading-relaxed', errors.body && 'border-danger')}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="What worked, what didn't, who should watch it… (**bold**, *italic*, > quotes, - lists)"
              aria-invalid={!!errors.body}
              aria-describedby={errors.body ? 'body-error' : undefined}
              disabled={busy}
            />
            {errors.body && (
              <p id="body-error" className="field-error">
                {errors.body}
              </p>
            )}
          </div>

          <label className="flex cursor-pointer items-center justify-between gap-4 rounded-xl border border-line bg-surface-2 p-3">
            <span className="flex items-center gap-3">
              <EyeOff className="h-5 w-5 text-amber" aria-hidden />
              <span>
                <span className="block text-sm font-medium">Contains spoilers</span>
                <span className="block text-xs text-muted">Readers will see a blur until they tap to reveal.</span>
              </span>
            </span>
            <input
              type="checkbox"
              role="switch"
              aria-checked={spoilers}
              checked={spoilers}
              onChange={(e) => setSpoilers(e.target.checked)}
              className="peer sr-only"
              disabled={busy}
            />
            <span
              aria-hidden
              className={cn(
                'relative h-6 w-11 shrink-0 rounded-full transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-amber peer-focus-visible:ring-offset-2 peer-focus-visible:ring-offset-bg',
                spoilers ? 'bg-amber' : 'bg-line',
              )}
            >
              <span
                className={cn(
                  'absolute top-0.5 h-5 w-5 rounded-full bg-ink transition-transform',
                  spoilers ? 'translate-x-[22px]' : 'translate-x-0.5',
                )}
              />
            </span>
          </label>
          <p className="-mt-3 flex items-center gap-1.5 px-1 text-xs text-muted">
            <Bot className="h-3.5 w-3.5 text-amber" aria-hidden />
            Our AI also checks every review for spoilers.
          </p>
        </section>

        {/* 3. image */}
        <section className="card space-y-4 p-4 sm:p-5" aria-labelledby="step-image">
          <h2 id="step-image" className="flex items-center gap-2 font-semibold">
            <ImageIcon className="h-4 w-4 text-amber" aria-hidden /> Image
          </h2>
          <SegmentedControl
            label="Image"
            value={imageType}
            onChange={(v) => {
              setImageType(v)
              setErrors((e) => ({ ...e, image: undefined }))
            }}
            segments={[
              { value: 'NONE', label: 'No image', icon: <Ban className="h-4 w-4" aria-hidden /> },
              { value: 'TMDB_POSTER', label: 'Movie poster', icon: <ImageIcon className="h-4 w-4" aria-hidden /> },
              { value: 'CUSTOM', label: 'Upload my own', icon: <Upload className="h-4 w-4" aria-hidden /> },
            ]}
          />
          {imageType === 'TMDB_POSTER' &&
            (movie ? (
              <div className="flex justify-center rounded-2xl border border-line bg-surface-2 p-4">
                <Poster url={movie.posterUrl} title={movie.title} className="max-h-[480px] w-56 rounded-xl shadow-2xl" />
              </div>
            ) : (
              <p className="rounded-xl border border-dashed border-line p-6 text-center text-sm text-muted">
                Pick a movie first and its poster will show up here.
              </p>
            ))}
          {imageType === 'CUSTOM' && (
            <ImageDropzone
              file={file}
              onChange={(f) => {
                setFile(f)
                setErrors((e) => ({ ...e, image: undefined }))
              }}
              error={errors.image}
            />
          )}
          {errors.image && imageType !== 'CUSTOM' && <p className="field-error">{errors.image}</p>}
        </section>

        {errors.form && (
          <p className="rounded-xl border border-danger/40 bg-danger/10 p-3 text-sm text-danger" role="alert">
            {errors.form}
          </p>
        )}

        <div className="flex justify-end gap-2 pb-4">
          <button type="button" className="btn btn-ghost" onClick={() => navigate(-1)} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary min-w-[140px]" disabled={busy}>
            {busy && <Spinner className="h-4 w-4 text-current" />}
            {busy ? 'Posting…' : 'Post review'}
          </button>
        </div>
      </form>
    </div>
  )
}
