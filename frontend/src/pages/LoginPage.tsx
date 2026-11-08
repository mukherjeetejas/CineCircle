import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { Logo } from '../components/Logo'
import { Spinner } from '../components/Spinner'
import { safeFrom } from '../lib/utils'

export function LoginPage() {
  const { status, signIn } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const from = safeFrom(location.state)
  const [usernameOrEmail, setUsernameOrEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (status === 'authenticated' && !busy) return <Navigate to={from} replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!usernameOrEmail.trim() || !password) {
      setError('Enter your username or email and password.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const res = await login({ usernameOrEmail: usernameOrEmail.trim(), password })
      await signIn(res)
      navigate(from, { replace: true })
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.status === 401 || err.status === 400
            ? 'Wrong username/email or password.'
            : err.message
          : 'Something went wrong.',
      )
      setBusy(false)
    }
  }

  return (
    <div className="card w-full max-w-sm p-6 sm:p-8">
      <div className="mb-6 text-center">
        <Logo asLink={false} className="text-3xl" />
        <h1 className="mt-4 text-xl font-bold">Welcome back</h1>
        <p className="mt-1 text-sm text-muted">Your circle's been posting.</p>
      </div>
      <form onSubmit={submit} className="space-y-4" noValidate>
        <div>
          <label htmlFor="usernameOrEmail" className="label">
            Username or email
          </label>
          <input
            id="usernameOrEmail"
            className="input"
            autoComplete="username"
            autoFocus
            value={usernameOrEmail}
            onChange={(e) => setUsernameOrEmail(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor="password" className="label">
            Password
          </label>
          <input
            id="password"
            type="password"
            className="input"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        {error && (
          <p className="rounded-xl bg-danger/10 p-3 text-sm text-danger" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="btn btn-primary w-full" disabled={busy}>
          {busy && <Spinner className="h-4 w-4 text-current" />}
          Log in
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        New here?{' '}
        <Link to="/register" state={location.state} className="font-semibold text-amber hover:underline">
          Create an account
        </Link>
      </p>
    </div>
  )
}
