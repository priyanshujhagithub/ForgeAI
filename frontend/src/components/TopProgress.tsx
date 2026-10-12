import { useEffect, useState } from 'react'
import { useIsFetching, useIsMutating } from '@tanstack/react-query'
import styles from './TopProgress.module.css'

// Waiting this long before showing means a quick request shows nothing at all.
const SHOW_AFTER_MS = 220
// Long enough for the bar to finish and fade once the work is done.
const HIDE_AFTER_MS = 500

/**
 * A thin line across the top of the window while something is loading for the
 * first time or being saved. Background refreshes, such as polling a run, do
 * not count: the page already has something to show.
 */
export function TopProgress() {
  const loading =
    useIsFetching({ predicate: (query) => query.state.status === 'pending' }) + useIsMutating() > 0
  const [shown, setShown] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => setShown(loading), loading ? SHOW_AFTER_MS : HIDE_AFTER_MS)
    return () => clearTimeout(timer)
  }, [loading])

  const phase = shown ? (loading ? 'loading' : 'done') : 'idle'

  return (
    <div className={styles.track} data-phase={phase} aria-hidden="true">
      <div className={styles.bar} />
    </div>
  )
}
