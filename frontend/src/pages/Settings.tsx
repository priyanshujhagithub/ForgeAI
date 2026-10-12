import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '../components/Button'
import { Skeleton } from '../components/Feedback'
import { ErrorNotice, Notice } from '../components/Notice'
import { Pill } from '../components/StatusPill'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { nth } from '../lib/motion'
import { useConnection } from '../lib/queries'
import { setTheme, useTheme } from '../lib/theme'
import type { ThemePreference } from '../lib/theme'
import shared from './shared.module.css'
import styles from './Settings.module.css'

export function Settings() {
  const { session, signOut } = useAuth()

  return (
    <div className={`${shared.stack} ${shared.narrow}`}>
      <header>
        <h1 className={shared.pageTitle}>Settings</h1>
      </header>

      <GitHubCard />

      <AppearanceCard />

      <section className={`${shared.card} ${styles.card}`} aria-labelledby="account-heading">
        <h2 id="account-heading" className={shared.sectionTitle}>
          Account
        </h2>
        <dl className={`${shared.meta} ${styles.accountMeta}`}>
          <div>
            <dt>Email</dt>
            <dd>{session?.email}</dd>
          </div>
          <div>
            <dt>Session ends</dt>
            <dd>
              {session &&
                new Date(session.expiresAt).toLocaleTimeString('en', {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
            </dd>
          </div>
        </dl>
        <Button onClick={signOut}>Sign out</Button>
      </section>
    </div>
  )
}

const THEMES: { value: ThemePreference; label: string }[] = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

/** Three radio buttons drawn as one control, with a thumb that slides to the choice. */
function AppearanceCard() {
  const { preference } = useTheme()
  const selected = THEMES.findIndex((theme) => theme.value === preference)

  return (
    <section className={`${shared.card} ${styles.card}`} aria-labelledby="appearance-heading">
      <h2 id="appearance-heading" className={shared.sectionTitle}>
        Appearance
      </h2>
      <p className="muted">
        System follows your device, and changes with it. The choice is kept in this browser.
      </p>
      <div
        role="radiogroup"
        aria-labelledby="appearance-heading"
        className={styles.segments}
        style={nth(selected)}
      >
        <span className={styles.thumb} aria-hidden="true" />
        {THEMES.map((theme) => (
          <label key={theme.value} className={styles.segment}>
            <input
              type="radio"
              name="theme"
              className="visually-hidden"
              value={theme.value}
              checked={theme.value === preference}
              onChange={(event) => {
                // The new theme spreads out from the option that was chosen.
                const box = event.currentTarget.parentElement!.getBoundingClientRect()
                setTheme(theme.value, {
                  x: box.left + box.width / 2,
                  y: box.top + box.height / 2,
                })
              }}
            />
            {theme.label}
          </label>
        ))}
      </div>
    </section>
  )
}

function GitHubCard() {
  const connection = useConnection()
  const [params] = useSearchParams()
  const [redirecting, setRedirecting] = useState(false)
  const [error, setError] = useState<unknown>(null)

  // Set by the API when GitHub sends the user back here after approval.
  const returned = params.get('github')

  async function connect() {
    setError(null)
    setRedirecting(true)
    try {
      // Leave the app for GitHub's consent screen. GitHub returns to the API,
      // which stores the token and redirects back to this page.
      window.location.assign(await api.authorizationUrl())
    } catch (caught) {
      setError(caught)
      setRedirecting(false)
    }
  }

  return (
    <section className={`${shared.card} ${styles.card}`} aria-labelledby="github-heading">
      <div className={styles.cardHead}>
        <h2 id="github-heading" className={shared.sectionTitle}>
          GitHub
        </h2>
        {connection.data && (
          <Pill tone={connection.data.connected ? 'ok' : 'neutral'}>
            {connection.data.connected ? 'Connected' : 'Not connected'}
          </Pill>
        )}
      </div>

      {returned === 'connected' && connection.data?.connected && (
        <Notice tone="ok" title="GitHub is connected" role="status">
          ForgeAI can now read your repositories and open pull requests.
        </Notice>
      )}
      {returned === 'error' && (
        <Notice tone="bad" title="GitHub was not connected" role="alert">
          The authorisation link had expired or was already used. Start the connection again.
        </Notice>
      )}
      {error !== null && <ErrorNotice error={error} title="Could not start the connection" />}
      {connection.isError && <ErrorNotice error={connection.error} />}

      {connection.isLoading && <Skeleton lines={2} />}

      {connection.data?.connected && (
        <p className="muted">
          Signed in to GitHub as <strong className="mono">{connection.data.githubUsername}</strong>.
          ForgeAI reads repositories with this account and opens pull requests as it. The access
          token is stored encrypted.
        </p>
      )}

      {connection.data?.connected === false && (
        <>
          <p className="muted">
            ForgeAI asks GitHub for the <code>repo</code> scope so it can read your code, push a
            new branch and open a pull request. It never pushes to your default branch.
          </p>
          <Button variant="primary" busy={redirecting} onClick={connect}>
            Connect GitHub
          </Button>
        </>
      )}
    </section>
  )
}
