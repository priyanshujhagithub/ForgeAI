import type { CSSProperties, ReactNode } from 'react'
import styles from './Feedback.module.css'

// Placeholders are shaped like the content they stand in for, so the page does
// not jump when the real thing arrives. They also wait a moment before
// showing: a response that comes back quickly never flashes a skeleton at all.

function bar(width: string, height?: string): CSSProperties {
  return { width, height }
}

/** Lines of text that have not loaded yet. */
export function Skeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className={styles.skeleton} role="status" aria-label="Loading">
      {Array.from({ length: lines }, (_, index) => (
        <span key={index} className={styles.bar} style={bar(`${92 - index * 14}%`)} />
      ))}
    </div>
  )
}

/** A project card that has not loaded yet. */
export function CardSkeleton() {
  return (
    <div className={`${styles.skeleton} ${styles.card}`} role="status" aria-label="Loading">
      <span className={styles.bar} style={bar('45%', '0.7rem')} />
      <span className={styles.bar} style={bar('70%', '1.35rem')} />
      <span className={styles.divider} />
      <span className={styles.bar} style={bar('38%', '1.25rem')} />
    </div>
  )
}

/** Rows of a list, each a title over a line of details, that have not loaded yet. */
export function RowSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className={styles.skeleton} role="status" aria-label="Loading">
      {Array.from({ length: rows }, (_, index) => (
        <span key={index} className={styles.row}>
          <span className={styles.bar} style={bar(`${78 - index * 12}%`, '1rem')} />
          <span className={styles.bar} style={bar('42%', '0.8rem')} />
        </span>
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
    <div className={`${styles.empty} enter`}>
      <h2 className={styles.emptyTitle}>{title}</h2>
      <p className={styles.emptyBody}>{children}</p>
      {action && <div className={styles.emptyAction}>{action}</div>}
    </div>
  )
}
