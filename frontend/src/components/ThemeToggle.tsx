import type { MouseEvent } from 'react'
import { setTheme, useTheme } from '../lib/theme'
import { Icon } from './Icon'
import styles from './ThemeToggle.module.css'

/** Switches between light and dark. The new theme spreads out from the button. */
export function ThemeToggle() {
  const { theme } = useTheme()
  const next = theme === 'dark' ? 'light' : 'dark'

  function onClick(event: MouseEvent<HTMLButtonElement>) {
    // The button's centre, not the pointer: the same for mouse, touch and keyboard.
    const box = event.currentTarget.getBoundingClientRect()
    setTheme(next, { x: box.left + box.width / 2, y: box.top + box.height / 2 })
  }

  return (
    <button
      type="button"
      className={styles.toggle}
      data-showing={theme}
      aria-label={`Switch to ${next} theme`}
      title={`Switch to ${next} theme`}
      onClick={onClick}
    >
      <Icon name="sun" className={`${styles.icon} ${styles.sun}`} />
      <Icon name="moon" className={`${styles.icon} ${styles.moon}`} />
    </button>
  )
}
