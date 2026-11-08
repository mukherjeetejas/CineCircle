import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Spinner } from '../components/Spinner'
import { useAuth } from './context'

interface Props {
  children: ReactNode
  /** Where anonymous users go. Defaults to /login (with return path). */
  redirectTo?: string
}

export function RequireAuth({ children, redirectTo }: Props) {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="flex justify-center py-16" role="status" aria-label="Loading">
        <Spinner />
      </div>
    )
  }
  if (status === 'anonymous') {
    return (
      <Navigate
        to={redirectTo ?? '/login'}
        replace
        state={redirectTo ? undefined : { from: location.pathname + location.search }}
      />
    )
  }
  return <>{children}</>
}
