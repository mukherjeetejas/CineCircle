import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { fetchMe } from '../api/auth'
import { setUnauthorizedHandler } from '../api/client'
import { qk } from '../api/keys'
import type { AuthResponse } from '../types'
import { AuthContext, type AuthContextValue } from './context'
import { getToken, setToken, subscribeToken } from './token'

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const [token, setTokenState] = useState<string | null>(() => getToken())

  useEffect(() => subscribeToken(setTokenState), [])

  // Keep the latest location in a ref-like closure for the 401 handler.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      qc.removeQueries({ queryKey: qk.me })
      if (!location.pathname.startsWith('/login')) {
        navigate('/login', { replace: true, state: { from: location.pathname + location.search } })
      }
    })
    return () => setUnauthorizedHandler(null)
  }, [qc, navigate, location])

  const meQuery = useQuery({
    queryKey: qk.me,
    queryFn: fetchMe,
    enabled: !!token,
    retry: false,
    staleTime: 60_000,
  })

  const signIn = useCallback(
    async (res: AuthResponse) => {
      // Drop anything fetched anonymously (upvotedByMe/isMine would be stale).
      qc.clear()
      // Contract: `user` is the full UserProfile, so no extra /users/me round-trip is needed.
      qc.setQueryData(qk.me, res.user)
      setToken(res.token)
    },
    [qc],
  )

  const signOut = useCallback(() => {
    setToken(null)
    qc.clear()
    navigate('/popular')
  }, [qc, navigate])

  const value = useMemo<AuthContextValue>(() => {
    const user = token ? (meQuery.data ?? null) : null
    let status: AuthContextValue['status']
    if (!token) status = 'anonymous'
    else if (user) status = 'authenticated'
    else if (meQuery.isError) status = 'anonymous'
    else status = 'loading'
    return { status, user, token, signIn, signOut }
  }, [token, meQuery.data, meQuery.isError, signIn, signOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
