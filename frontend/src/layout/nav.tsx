import { Flame, Home, Info, Plus, User, UserPlus } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { useFollowRequests } from '../api/users'
import { useAuth } from '../auth/context'
import { cn } from '../lib/utils'

function Badge({ count }: { count: number }) {
  if (count <= 0) return null
  return (
    <span
      className="ml-auto inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-amber px-1.5 text-[11px] font-bold text-black"
      aria-label={`${count} pending`}
    >
      {count > 99 ? '99+' : count}
    </span>
  )
}

function useRequestCount(): number {
  const { status } = useAuth()
  const { data } = useFollowRequests(status === 'authenticated')
  return data?.length ?? 0
}

export function LeftRail() {
  const { user, status } = useAuth()
  const requests = useRequestCount()
  const link = ({ isActive }: { isActive: boolean }) =>
    cn(
      'flex items-center gap-3 rounded-xl px-3 py-2.5 text-[15px] font-medium transition-colors',
      isActive ? 'bg-amber-muted text-amber' : 'text-ink/85 hover:bg-surface-2 hover:text-ink',
    )

  return (
    <nav aria-label="Main" className="space-y-1">
      <NavLink to="/" end className={link}>
        <Home className="h-5 w-5" aria-hidden /> Home
      </NavLink>
      <NavLink to="/popular" className={link}>
        <Flame className="h-5 w-5" aria-hidden /> Popular
      </NavLink>
      {status === 'authenticated' && user && (
        <>
          <NavLink to={`/u/${user.username}`} className={link}>
            <User className="h-5 w-5" aria-hidden /> My Profile
          </NavLink>
          <NavLink to="/requests" className={link}>
            <UserPlus className="h-5 w-5" aria-hidden /> Follow requests
            <Badge count={requests} />
          </NavLink>
        </>
      )}
      <NavLink to="/about" className={link}>
        <Info className="h-5 w-5" aria-hidden /> About
      </NavLink>
      {status === 'authenticated' && (
        <div className="pt-3">
          <NavLink to="/submit" className="btn btn-primary w-full">
            <Plus className="h-4 w-4" aria-hidden /> Write a review
          </NavLink>
        </div>
      )}
      {status === 'anonymous' && (
        <div className="mt-4 rounded-2xl border border-line bg-surface p-4">
          <p className="font-display text-sm font-semibold">Got opinions?</p>
          <p className="mt-1 text-xs text-muted">Join to post reviews, follow friends and upvote hot takes.</p>
          <NavLink to="/register" className="btn btn-primary btn-sm mt-3 w-full">
            Join cinecircle
          </NavLink>
        </div>
      )}
    </nav>
  )
}

export function BottomNav() {
  const { user, status } = useAuth()
  const requests = useRequestCount()
  const link = ({ isActive }: { isActive: boolean }) =>
    cn(
      'relative flex flex-1 flex-col items-center gap-0.5 py-2 text-[10px] font-medium',
      isActive ? 'text-amber' : 'text-muted',
    )
  const authed = status === 'authenticated' && user

  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-40 flex border-t border-line bg-bg/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden"
    >
      <NavLink to="/" end className={link}>
        <Home className="h-5 w-5" aria-hidden /> Home
      </NavLink>
      <NavLink to="/popular" className={link}>
        <Flame className="h-5 w-5" aria-hidden /> Popular
      </NavLink>
      <NavLink to={authed ? '/submit' : '/login'} className={link} aria-label="Create post">
        <span className="-mt-1 flex h-9 w-9 items-center justify-center rounded-full bg-amber text-black shadow-glow-lg">
          <Plus className="h-5 w-5" aria-hidden />
        </span>
      </NavLink>
      <NavLink to={authed ? '/requests' : '/about'} className={link}>
        {authed ? <UserPlus className="h-5 w-5" aria-hidden /> : <Info className="h-5 w-5" aria-hidden />}
        {authed ? 'Requests' : 'About'}
        {authed && requests > 0 && (
          <span className="absolute right-[calc(50%-18px)] top-1 h-2 w-2 rounded-full bg-amber" aria-label={`${requests} pending`} />
        )}
      </NavLink>
      <NavLink to={authed ? `/u/${user.username}` : '/login'} className={link}>
        <User className="h-5 w-5" aria-hidden /> {authed ? 'Profile' : 'Log in'}
      </NavLink>
    </nav>
  )
}
