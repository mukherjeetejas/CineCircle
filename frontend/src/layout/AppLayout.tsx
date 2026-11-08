import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { TmdbFooterLine } from '../components/TmdbAttribution'
import { BottomNav, LeftRail } from './nav'
import { RightRail } from './RightRail'
import { TopBar } from './TopBar'

function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return null
}

/** Reddit-style shell: sticky top bar, left rail (md+), centre column (≤640px), right rail (lg+). */
export function AppLayout() {
  return (
    <div className="min-h-screen">
      <ScrollToTop />
      <TopBar />
      <div className="mx-auto flex max-w-[1280px] justify-center gap-6 px-3 pb-24 pt-4 sm:px-4 md:pb-10">
        <aside className="sticky top-[72px] hidden h-[calc(100vh-88px)] w-56 shrink-0 overflow-y-auto md:block">
          <LeftRail />
        </aside>
        <main id="main" tabIndex={-1} className="w-full min-w-0 max-w-[640px] flex-1 focus:outline-none">
          <Outlet />
          <TmdbFooterLine className="mt-10 justify-center text-center lg:hidden" />
        </main>
        <aside className="sticky top-[72px] hidden h-[calc(100vh-88px)] w-80 shrink-0 overflow-y-auto lg:block">
          <RightRail />
        </aside>
      </div>
      <BottomNav />
    </div>
  )
}

/** Minimal centred shell for login/register. */
export function AuthLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <TopBar />
      <main id="main" className="flex flex-1 items-start justify-center px-4 py-10 sm:items-center">
        <Outlet />
      </main>
      <TmdbFooterLine className="justify-center px-4 pb-6 text-center" />
    </div>
  )
}
