import { ButtonLink } from '../components/Button'
import { useAuth } from '../lib/auth'
import styles from './NotFound.module.css'

/** Shown for an unknown URL, and by pages whose project or run does not exist. */
export function NotFound({ what = 'page' }: { what?: string }) {
  const { session } = useAuth()

  return (
    <div className={styles.wrap}>
      <p className={styles.code}>404</p>
      <h1 className={styles.title}>This {what} does not exist.</h1>
      <p className={styles.text}>
        It may have been deleted, or the link may be wrong. It may also belong to another
        account.
      </p>
      <ButtonLink to={session ? '/app' : '/'} variant="primary">
        {session ? 'Back to projects' : 'Back to the home page'}
      </ButtonLink>
    </div>
  )
}
