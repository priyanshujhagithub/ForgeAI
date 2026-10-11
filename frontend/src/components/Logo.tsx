import styles from './Logo.module.css'

/** The mark is a block letter F with one ember-coloured square: the spark. */
export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <span className={styles.logo}>
      <svg viewBox="0 0 32 32" aria-hidden="true" className={styles.mark}>
        <rect width="32" height="32" rx="6" className={styles.plate} />
        <path d="M9 8h14v4H13v4h8v4h-8v4H9z" className={styles.letter} />
        <rect x="21" y="20" width="4" height="4" className={styles.spark} />
      </svg>
      {!compact && <span className={styles.word}>ForgeAI</span>}
    </span>
  )
}
