import { useLayoutEffect, useRef } from 'react'
import { Outlet, ScrollRestoration, useLocation } from 'react-router'
import { TopProgress } from './TopProgress'

/** How deep a page sits: home, then a section, then a project, then one of its runs. */
function depth(path: string): number {
  if (path === '/') return 0
  if (path.startsWith('/app/runs/')) return 3
  if (path.startsWith('/app/projects/')) return 2
  return 1
}

/**
 * Wraps every page. It tells the page transition which way to move: going
 * deeper slides the new page in from the right, going back from the left, and
 * moving between pages at the same level only cross-fades.
 *
 * React Router applies a navigation inside the view transition's update, so
 * this layout effect runs before the browser draws the first frame of it.
 */
export function RootLayout() {
  const { pathname } = useLocation()
  const previous = useRef(pathname)

  useLayoutEffect(() => {
    if (previous.current === pathname) return
    const change = depth(pathname) - depth(previous.current)
    document.documentElement.dataset.nav = change > 0 ? 'forward' : change < 0 ? 'back' : 'level'
    previous.current = pathname
  }, [pathname])

  return (
    <>
      <TopProgress />
      <Outlet />
      {/* A new page opens at the top; going back returns to where you had scrolled. */}
      <ScrollRestoration />
    </>
  )
}
