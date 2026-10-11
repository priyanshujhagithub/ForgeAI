import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from '../lib/auth'
import { Button } from './Button'
import { Logo } from './Logo'
import styles from './AppShell.module.css'

const NAV = [
  { to: '/app', label: 'Projects', end: true },
  { to: '/app/settings', label: 'Settings', end: false },
]

/** The frame around every signed-in page: brand, navigation, account. */
export function AppShell() {
  const { session, signOut } = useAuth()

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
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                viewTransition
                className={({ isActive }) => `${styles.link} ${isActive ? styles.current : ''}`}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className={styles.account}>
            <span className={styles.email} title={session?.email}>
              {session?.email}
            </span>
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
