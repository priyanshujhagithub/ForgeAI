import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { Button } from '../components/Button'
import { ExampleRun } from '../components/ExampleRun'
import { TextField } from '../components/Field'
import { Logo } from '../components/Logo'
import { ErrorNotice, Notice } from '../components/Notice'
import { ThemeToggle } from '../components/ThemeToggle'
import { useAuth } from '../lib/auth'
import { nth } from '../lib/motion'
import styles from './Auth.module.css'

const MIN_PASSWORD = 8

// A sign-in slower than this is almost always a server starting up.
const SLOW_MS = 4000

const COPY = {
  login: {
    title: 'Sign in',
    lede: 'Welcome back. Your projects and runs are where you left them.',
    submit: 'Sign in',
    switchText: 'New to ForgeAI?',
    switchLink: 'Create an account',
    switchTo: '/register',
  },
  register: {
    title: 'Create your account',
    lede: 'An account keeps your projects, your GitHub connection and your run history.',
    submit: 'Create account',
    switchText: 'Already have an account?',
    switchLink: 'Sign in',
    switchTo: '/login',
  },
}

/** True once `busy` has lasted longer than SLOW_MS, until it ends. */
function useSlow(busy: boolean): boolean {
  const [slow, setSlow] = useState(false)
  useEffect(() => {
    if (!busy) return
    const timer = setTimeout(() => setSlow(true), SLOW_MS)
    return () => {
      clearTimeout(timer)
      setSlow(false)
    }
  }, [busy])
  return slow
}

/** Sign-in and registration share one form; `mode` picks the wording and the call. */
export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { session, signIn, signUp } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const copy = COPY[mode]

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const slow = useSlow(busy)

  // Where the user was heading before being asked to sign in.
  const destination = (location.state as { from?: string } | null)?.from ?? '/app'

  if (session) return <Navigate to={destination} replace />

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (mode === 'register' && password.length < MIN_PASSWORD) {
      setPasswordError(`Use at least ${MIN_PASSWORD} characters.`)
      return
    }
    setPasswordError(null)

    setBusy(true)
    try {
      await (mode === 'login' ? signIn(email, password) : signUp(email, password))
      navigate(destination, { replace: true, viewTransition: true })
    } catch (caught) {
      setError(caught)
      setBusy(false)
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.side}>
        <header className={styles.header}>
          <Link to="/" viewTransition className={styles.brand} aria-label="ForgeAI home">
            <Logo />
          </Link>
          <ThemeToggle />
        </header>

        <main className={styles.main}>
          <div className={styles.card}>
            {/* Shared between sign-in and registration, so it is left out of the entrance. */}
            <h1 className={`${styles.title} vt-auth-title`}>{copy.title}</h1>
            <p className={`${styles.lede} enter`} style={nth(0)}>
              {copy.lede}
            </p>

            <form className={`${styles.form} enter`} style={nth(1)} onSubmit={onSubmit}>
              {error !== null && (
                <ErrorNotice
                  error={error}
                  title={mode === 'login' ? 'Could not sign in' : 'Could not create the account'}
                />
              )}

              <TextField
                label="Email"
                type="email"
                name="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
              <TextField
                label="Password"
                type="password"
                name="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                required
                value={password}
                error={passwordError}
                hint={mode === 'register' ? `At least ${MIN_PASSWORD} characters.` : undefined}
                onChange={(event) => setPassword(event.target.value)}
              />

              <Button type="submit" variant="primary" size="lg" block busy={busy}>
                {copy.submit}
              </Button>

              {slow && (
                <Notice tone="busy" title="Waking the server" role="status">
                  ForgeAI runs on free hosting, which stops the server when nobody is using it.
                  The first sign-in after a quiet spell can take a minute or two.
                </Notice>
              )}
            </form>

            <p className={`${styles.switch} enter`} style={nth(2)}>
              {copy.switchText}{' '}
              <Link to={copy.switchTo} state={location.state} viewTransition>
                {copy.switchLink}
              </Link>
            </p>
          </div>
        </main>
      </div>

      {/* Wide screens only. It stays put while the form changes beside it. */}
      <aside className={`${styles.aside} vt-auth-aside`} aria-label="What a run looks like">
        <div className={styles.asideInner}>
          <p className="eyebrow">What a run looks like</p>
          <ExampleRun />
          <p className={styles.asideNote}>
            Every run ends in a pull request for you to review, or in nothing at all.
          </p>
        </div>
      </aside>
    </div>
  )
}
