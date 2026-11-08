import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { register } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { Logo } from '../components/Logo'
import { Spinner } from '../components/Spinner'
import { cn, safeFrom } from '../lib/utils'

type Field = 'username' | 'email' | 'password' | 'displayName'
const USERNAME_RE = /^[a-z0-9_]{3,20}$/

export function RegisterPage() {
  const { status, signIn } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [form, setForm] = useState<Record<Field, string>>({ username: '', email: '', password: '', displayName: '' })
  const [errors, setErrors] = useState<Partial<Record<Field | 'form', string>>>({})
  const [busy, setBusy] = useState(false)

  if (status === 'authenticated' && !busy) return <Navigate to="/" replace />

  const set = (k: Field) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = k === 'username' ? e.target.value.toLowerCase() : e.target.value
    setForm((f) => ({ ...f, [k]: v }))
    setErrors((er) => ({ ...er, [k]: undefined }))
  }

  const validate = () => {
    const e: Partial<Record<Field, string>> = {}
    if (!USERNAME_RE.test(form.username)) e.username = '3–20 characters: lowercase letters, numbers, underscores.'
    if (!/^\S+@\S+\.\S+$/.test(form.email)) e.email = 'Enter a valid email.'
    if (form.password.length < 8 || form.password.length > 100) e.password = 'Password must be 8–100 characters.'
    if (!form.displayName.trim()) e.displayName = 'What should we call you?'
    return e
  }

  const submit = async (ev: FormEvent) => {
    ev.preventDefault()
    const e = validate()
    setErrors(e)
    if (Object.keys(e).length) return
    setBusy(true)
    try {
      const res = await register({ ...form, displayName: form.displayName.trim(), email: form.email.trim() })
      await signIn(res)
      toast.success(`Welcome to the circle, ${form.displayName.trim().split(' ')[0]}`)
      navigate(safeFrom(location.state), { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        const fe = err.fieldErrors as Partial<Record<Field, string>>
        setErrors({ ...fe, form: Object.keys(fe).length ? undefined : err.message })
      } else {
        setErrors({ form: 'Something went wrong.' })
      }
      setBusy(false)
    }
  }

  const field = (k: Field, label: string, props: React.InputHTMLAttributes<HTMLInputElement>, hint?: string) => (
    <div>
      <label htmlFor={k} className="label">
        {label}
      </label>
      <input
        id={k}
        className={cn('input', errors[k] && 'border-danger')}
        value={form[k]}
        onChange={set(k)}
        aria-invalid={!!errors[k]}
        aria-describedby={`${k}-help`}
        {...props}
      />
      <p id={`${k}-help`} className={errors[k] ? 'field-error' : 'mt-1 text-xs text-muted'}>
        {errors[k] ?? hint}
      </p>
    </div>
  )

  return (
    <div className="card w-full max-w-sm p-6 sm:p-8">
      <div className="mb-6 text-center">
        <Logo asLink={false} className="text-3xl" />
        <h1 className="mt-4 text-xl font-bold">Join the circle</h1>
        <p className="mt-1 text-sm text-muted">Rate movies. Follow friends. Start discourse.</p>
      </div>
      <form onSubmit={submit} className="space-y-3" noValidate>
        {field('displayName', 'Display name', { autoComplete: 'name', autoFocus: true, maxLength: 50 })}
        {field('username', 'Username', { autoComplete: 'username', maxLength: 20 }, 'Lowercase letters, numbers, underscores.')}
        {field('email', 'Email', { type: 'email', autoComplete: 'email' })}
        {field('password', 'Password', { type: 'password', autoComplete: 'new-password', maxLength: 100 }, '8–100 characters.')}
        {errors.form && (
          <p className="rounded-xl bg-danger/10 p-3 text-sm text-danger" role="alert">
            {errors.form}
          </p>
        )}
        <button type="submit" className="btn btn-primary w-full" disabled={busy}>
          {busy && <Spinner className="h-4 w-4 text-current" />}
          Create account
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        Already have an account?{' '}
        <Link to="/login" state={location.state} className="font-semibold text-amber hover:underline">
          Log in
        </Link>
      </p>
    </div>
  )
}
