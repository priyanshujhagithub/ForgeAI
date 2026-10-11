import type { ReactNode } from 'react'
import styles from './Feedback.module.css'

/** A grey block that holds the place of content while it loads. */
export function Skeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className={styles.skeleton} role="status" aria-label="Loading">
      {Array.from({ length: lines }, (_, index) => (
        <span key={index} className={styles.bar} style={{ width: `${92 - index * 14}%` }} />
      ))}
    </div>
  )
}

interface EmptyStateProps {
  title: string
  children: ReactNode
  action?: ReactNode
}

/** What a page shows when there is nothing yet: why, and the one thing to do next. */
export function EmptyState({ title, children, action }: EmptyStateProps) {
  return (
    <div className={styles.empty}>
      <h2 className={styles.emptyTitle}>{title}</h2>
      <p className={styles.emptyBody}>{children}</p>
      {action && <div className={styles.emptyAction}>{action}</div>}
    </div>
  )
}
