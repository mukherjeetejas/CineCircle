import { ChevronDown, Info, LogOut, Plus, Search, Settings, User, UserPlus, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { Avatar } from '../components/Avatar'
import { GlobalSearch } from '../components/GlobalSearch'
import { Logo } from '../components/Logo'
import { useDismiss } from '../lib/hooks'

function AvatarMenu() {
  const { user, signOut } = useAuth()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  useDismiss(open, () => setOpen(false), ref)
  if (!user) return null

  const item = 'flex w-full items-center gap-3 rounded-xl px-3 py-2 text-sm hover:bg-surface-2 focus-visible:bg-surface-2'
  const go = (to: string) => {
    setOpen(false)
    navigate(to)
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account menu"
        className="flex items-center gap-1 rounded-full p-0.5 pr-1.5 hover:bg-surface-2"
      >
        <Avatar url={user.avatarUrl} name={user.displayName || user.username} size="sm" />
        <ChevronDown className="hidden h-4 w-4 text-muted sm:block" aria-hidden />
      </button>
      {open && (
        <div
          role="menu"
          aria-label="Account"
          className="absolute right-0 z-50 mt-2 w-60 rounded-2xl border border-line bg-surface p-1.5 shadow-2xl"
          onKeyDown={(e) => {
            const items = Array.from(e.currentTarget.querySelectorAll<HTMLElement>('[role="menuitem"]'))
            const i = items.indexOf(document.activeElement as HTMLElement)
            if (e.key === 'ArrowDown') {
              e.preventDefault()
              items[(i + 1) % items.length]?.focus()
            } else if (e.key === 'ArrowUp') {
              e.preventDefault()
              items[(i - 1 + items.length) % items.length]?.focus()
            }
          }}
        >
          <div className="px-3 py-2">
            <div className="truncate font-semibold">{user.displayName || user.username}</div>
            <div className="truncate text-xs text-muted">@{user.username}</div>
          </div>
          <div className="my-1 h-px bg-line" />
          <button role="menuitem" autoFocus className={item} onClick={() => go(`/u/${user.username}`)}>
            <User className="h-4 w-4 text-muted" aria-hidden /> My profile
          </button>
          <button role="menuitem" className={item} onClick={() => go('/requests')}>
            <UserPlus className="h-4 w-4 text-muted" aria-hidden /> Follow requests
          </button>
          <button role="menuitem" className={item} onClick={() => go('/settings')}>
            <Settings className="h-4 w-4 text-muted" aria-hidden /> Settings
          </button>
          <button role="menuitem" className={item} onClick={() => go('/about')}>
            <Info className="h-4 w-4 text-muted" aria-hidden /> About
          </button>
          <div className="my-1 h-px bg-line" />
          <button
            role="menuitem"
            className={`${item} text-danger`}
            onClick={() => {
              setOpen(false)
              signOut()
            }}
          >
            <LogOut className="h-4 w-4" aria-hidden /> Log out
          </button>
        </div>
      )}
    </div>
  )
}

export function TopBar() {
  const { status } = useAuth()
  const [mobileSearch, setMobileSearch] = useState(false)

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-bg/85 backdrop-blur-md">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:z-50 focus:rounded-full focus:bg-amber focus:px-4 focus:py-2 focus:text-black"
      >
        Skip to content
      </a>
      <div className="mx-auto flex h-14 max-w-[1280px] items-center gap-3 px-3 sm:px-4">
        {mobileSearch ? (
          <>
            <GlobalSearch className="flex-1" autoFocus onNavigate={() => setMobileSearch(false)} />
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => setMobileSearch(false)}
              aria-label="Close search"
            >
              <X className="h-5 w-5" aria-hidden />
            </button>
          </>
        ) : (
          <>
            <Logo />
            <GlobalSearch className="mx-auto hidden w-full max-w-xl md:block" />
            <div className="ml-auto flex items-center gap-2 md:ml-0">
              <button
                type="button"
                className="rounded-full p-2 text-muted hover:bg-surface-2 hover:text-ink md:hidden"
                onClick={() => setMobileSearch(true)}
                aria-label="Search"
              >
                <Search className="h-5 w-5" aria-hidden />
              </button>
              {status === 'authenticated' ? (
                <>
                  <Link to="/submit" className="btn btn-primary hidden sm:inline-flex">
                    <Plus className="h-4 w-4" aria-hidden /> Create Post
                  </Link>
                  <AvatarMenu />
                </>
              ) : status === 'anonymous' ? (
                <>
                  <Link to="/login" className="btn btn-ghost btn-sm">
                    Log in
                  </Link>
                  <Link to="/register" className="btn btn-primary btn-sm">
                    Sign up
                  </Link>
                </>
              ) : (
                <div className="skeleton h-8 w-8 rounded-full" aria-hidden />
              )}
            </div>
          </>
        )}
      </div>
    </header>
  )
}
