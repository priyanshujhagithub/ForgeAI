import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Link } from 'react-router'
import type { LinkProps } from 'react-router'
import styles from './Button.module.css'

type Variant = 'primary' | 'secondary' | 'quiet' | 'danger'
type Size = 'md' | 'lg'

interface Appearance {
  variant?: Variant
  size?: Size
  block?: boolean
}

function classes({ variant = 'secondary', size = 'md', block }: Appearance, extra?: string) {
  return [styles.button, styles[variant], styles[size], block && styles.block, extra]
    .filter(Boolean)
    .join(' ')
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, Appearance {
  /** Shows a spinner and blocks further clicks while work is in progress. */
  busy?: boolean
  children: ReactNode
}

export function Button({
  variant,
  size,
  block,
  busy = false,
  disabled,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={classes({ variant, size, block }, className)}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      {...rest}
    >
      {busy && <span className={styles.spinner} aria-hidden="true" />}
      {children}
    </button>
  )
}

/** A router link that looks like a button and animates with a view transition. */
export function ButtonLink({
  variant,
  size,
  block,
  className,
  children,
  ...rest
}: LinkProps & Appearance) {
  return (
    <Link viewTransition className={classes({ variant, size, block }, className)} {...rest}>
      {children}
    </Link>
  )
}

/** A plain anchor for leaving the app, such as opening a pull request on GitHub. */
export function ButtonAnchor({
  variant,
  size,
  block,
  className,
  children,
  href,
}: Appearance & { href: string; className?: string; children: ReactNode }) {
  return (
    <a
      className={classes({ variant, size, block }, className)}
      href={href}
      target="_blank"
      rel="noreferrer"
    >
      {children}
      <span aria-hidden="true">↗</span>
      <span className="visually-hidden">(opens in a new tab)</span>
    </a>
  )
}
