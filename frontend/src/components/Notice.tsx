import type { ReactNode } from 'react'
import type { Tone } from '../lib/runStatus'
import styles from './Notice.module.css'

interface NoticeProps {
  tone?: Tone
  title?: string
  children?: ReactNode
  /** Errors are announced to screen readers immediately. */
  role?: 'alert' | 'status'
}

export function Notice({ tone = 'neutral', title, children, role }: NoticeProps) {
  return (
    <div className={`${styles.notice} ${styles[tone]}`} role={role}>
      {title && <p className={styles.title}>{title}</p>}
      {children && <div className={styles.body}>{children}</div>}
    </div>
  )
}

/** The standard way to show a failed request: what went wrong, in the API's own words. */
export function ErrorNotice({ error, title = 'Something went wrong' }: { error: unknown; title?: string }) {
  const message = error instanceof Error ? error.message : 'An unexpected error occurred.'
  return (
    <Notice tone="bad" title={title} role="alert">
      {message}
    </Notice>
  )
}
