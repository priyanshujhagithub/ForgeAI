import { Link, Outlet, useLocation } from 'react-router'
import { useAuth } from '../lib/auth'
import { Button } from './Button'
import { Logo } from './Logo'
import { ThemeToggle } from './ThemeToggle'
import styles from './AppShell.module.css'

const SETTINGS = '/app/settings'

// A section stays marked while you are on any page inside it: a project and
// its runs are still "Projects".
const NAV = [
  { to: '/app', label: 'Projects', contains: (path: string) => !path.startsWith(SETTINGS) },
  { to: SETTINGS, label: 'Settings', contains: (path: string) => path.startsWith(SETTINGS) },
]

/** The frame around every signed-in page: brand, navigation, account. */
export function AppShell() {
  const { session, signOut } = useAuth()
  const { pathname } = useLocation()

  return (
    <>
      <a className={styles.skip} href="#main">
        Skip to content
      </a>

      <header className={`${styles.header} vt-header`}>
        <div className={`container ${styles.bar}`}>
          <Link to="/app" viewTransition className={styles.brand} aria-label="ForgeAI, projects">
            <Logo />
          </Link>

          <nav className={styles.nav} aria-label="Main">
            {NAV.map((item) => {
              const current = item.contains(pathname)
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  viewTransition
                  aria-current={pathname === item.to ? 'page' : current ? 'true' : undefined}
                  className={`${styles.link} ${current ? styles.current : ''}`}
                >
                  {item.label}
                  {/* One line, rendered under whichever section is current. Being a
                      named view-transition element, it slides when the section changes. */}
                  {current && (
                    <span className={`${styles.indicator} vt-nav-indicator`} aria-hidden="true" />
                  )}
                </Link>
              )
            })}
          </nav>

          <div className={styles.account}>
            <span className={styles.email} title={session?.email}>
              {session?.email}
            </span>
            <ThemeToggle />
            <Button variant="quiet" onClick={signOut}>
              Sign out
            </Button>
          </div>
        </div>
      </header>

      <main id="main" className={`container ${styles.main}`}>
        <Outlet />
      </main>
    </>
  )
}
