import { STATUS } from '../lib/runStatus'
import type { Tone } from '../lib/runStatus'
import type { RunStatus } from '../lib/types'
import styles from './StatusPill.module.css'

export function StatusPill({ status }: { status: RunStatus }) {
  const { label, tone } = STATUS[status]
  return <Pill tone={tone}>{label}</Pill>
}

/** Colour is never the only signal: the label always says what the state is. */
export function Pill({ tone, children }: { tone: Tone; children: string }) {
  return (
    <span className={`${styles.pill} ${styles[tone]}`}>
      <span className={styles.dot} aria-hidden="true" />
      {children}
    </span>
  )
}
