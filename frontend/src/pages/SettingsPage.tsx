import { Camera, Lock } from 'lucide-react'
import { useRef, useState, type FormEvent } from 'react'
import toast from 'react-hot-toast'
import { ApiError } from '../api/client'
import { useUpdateMe, useUploadAvatar } from '../api/users'
import { useAuth } from '../auth/context'
import { Avatar } from '../components/Avatar'
import { PageHeader } from '../components/PageHeader'
import { Spinner } from '../components/Spinner'
import { UseWithClaudePanel } from '../components/UseWithClaudePanel'
import { cn, MAX_IMAGE_LABEL, validateImage } from '../lib/utils'
import type { UserProfile } from '../types'

const MAX_BIO = 160

function SettingsForm({ user }: { user: UserProfile }) {
  const [displayName, setDisplayName] = useState(user.displayName ?? '')
  const [bio, setBio] = useState(user.bio ?? '')
  const [isPrivate, setIsPrivate] = useState(user.isPrivate)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const update = useUpdateMe()
  const upload = useUploadAvatar()
  const fileRef = useRef<HTMLInputElement>(null)

  const dirty = displayName !== (user.displayName ?? '') || bio !== (user.bio ?? '') || isPrivate !== user.isPrivate

  const onAvatar = (file: File | undefined) => {
    if (!file) return
    const err = validateImage(file)
    if (err) {
      toast.error(err)
      return
    }
    upload.mutate(file, {
      onSuccess: () => toast.success('New look, who dis'),
      onSettled: () => {
        if (fileRef.current) fileRef.current.value = ''
      },
    })
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!displayName.trim()) errs.displayName = 'Display name is required.'
    if (bio.length > MAX_BIO) errs.bio = `Bio must be ${MAX_BIO} characters or fewer.`
    setErrors(errs)
    if (Object.keys(errs).length) return
    update.mutate(
      { displayName: displayName.trim(), bio, isPrivate },
      {
        onSuccess: () => toast.success('Settings saved'),
        onError: (err) => {
          if (err instanceof ApiError) {
            setErrors(err.fieldErrors)
            if (!err.toasted) toast.error(err.message)
          }
        },
      },
    )
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <section className="card flex items-center gap-4 p-4 sm:p-5" aria-label="Avatar">
        <div className="relative">
          <Avatar url={user.avatarUrl} name={user.displayName || user.username} size="xl" />
          {upload.isPending && (
            <div className="absolute inset-0 flex items-center justify-center rounded-full bg-black/60">
              <Spinner />
            </div>
          )}
        </div>
        <div>
          <div className="font-semibold">Profile picture</div>
          <p className="mb-2 text-xs text-muted">JPEG, PNG or WebP, up to {MAX_IMAGE_LABEL}.</p>
          <label className={cn('btn btn-outline btn-sm cursor-pointer focus-within:ring-2 focus-within:ring-amber', upload.isPending && 'opacity-50')}>
            <Camera className="h-4 w-4" aria-hidden /> Upload new
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="sr-only"
              disabled={upload.isPending}
              onChange={(e) => onAvatar(e.target.files?.[0])}
            />
          </label>
        </div>
      </section>

      <section className="card space-y-4 p-4 sm:p-5" aria-label="Profile">
        <div>
          <label htmlFor="displayName" className="label">
            Display name
          </label>
          <input
            id="displayName"
            className={cn('input', errors.displayName && 'border-danger')}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            maxLength={50}
            aria-invalid={!!errors.displayName}
          />
          {errors.displayName && <p className="field-error">{errors.displayName}</p>}
        </div>
        <div>
          <div className="flex items-baseline justify-between">
            <label htmlFor="bio" className="label">
              Bio
            </label>
            <span className={cn('text-xs tabular-nums', bio.length > MAX_BIO ? 'text-danger' : 'text-muted')}>
              {bio.length}/{MAX_BIO}
            </span>
          </div>
          <textarea
            id="bio"
            rows={3}
            className={cn('input resize-none', errors.bio && 'border-danger')}
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            placeholder="Letterboxd-core. Will defend Cats (2019)."
            aria-invalid={!!errors.bio}
          />
          {errors.bio && <p className="field-error">{errors.bio}</p>}
        </div>
        <div className="text-sm text-muted">
          Username <span className="text-ink">@{user.username}</span> can't be changed.
        </div>
      </section>

      <section className="card p-4 sm:p-5" aria-label="Privacy">
        <label className="flex cursor-pointer items-center justify-between gap-4">
          <span className="flex items-center gap-3">
            <Lock className="h-5 w-5 text-amber" aria-hidden />
            <span>
              <span className="block text-sm font-medium">Private account</span>
              <span className="block text-xs text-muted">
                Only approved followers see your reviews. New follows become requests.
              </span>
            </span>
          </span>
          <input
            type="checkbox"
            role="switch"
            aria-checked={isPrivate}
            checked={isPrivate}
            onChange={(e) => setIsPrivate(e.target.checked)}
            className="peer sr-only"
          />
          <span
            aria-hidden
            className={cn(
              'relative h-6 w-11 shrink-0 rounded-full transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-amber peer-focus-visible:ring-offset-2 peer-focus-visible:ring-offset-bg',
              isPrivate ? 'bg-amber' : 'bg-line',
            )}
          >
            <span
              className={cn(
                'absolute top-0.5 h-5 w-5 rounded-full bg-ink transition-transform',
                isPrivate ? 'translate-x-[22px]' : 'translate-x-0.5',
              )}
            />
          </span>
        </label>
      </section>

      <div className="flex justify-end">
        <button type="submit" className="btn btn-primary min-w-[120px]" disabled={!dirty || update.isPending}>
          {update.isPending && <Spinner className="h-4 w-4 text-current" />}
          Save changes
        </button>
      </div>
    </form>
  )
}

export function SettingsPage() {
  const { user } = useAuth()
  if (!user) return null
  return (
    <div>
      <PageHeader title="Settings" subtitle="Tune your profile and privacy." />
      {/* key: reset the form when the saved profile changes */}
      <SettingsForm key={`${user.displayName}|${user.bio}|${user.isPrivate}`} user={user} />
      <div className="mt-6 pb-4">
        <UseWithClaudePanel />
      </div>
    </div>
  )
}
