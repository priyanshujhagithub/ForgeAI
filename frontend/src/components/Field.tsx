import { useId } from 'react'
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import styles from './Field.module.css'

interface FieldChrome {
  label: string
  hint?: ReactNode
  error?: string | null
}

/** Label, optional hint and error message, wired to the control for screen readers. */
function Chrome({
  id,
  label,
  hint,
  error,
  children,
}: FieldChrome & { id: string; children: ReactNode }) {
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>
      {children}
      {hint && !error && (
        <p className={styles.hint} id={`${id}-hint`}>
          {hint}
        </p>
      )}
      {error && (
        <p className={styles.error} id={`${id}-error`} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

function describedBy(id: string, hint: ReactNode, error?: string | null) {
  if (error) return `${id}-error`
  return hint ? `${id}-hint` : undefined
}

export function TextField({
  label,
  hint,
  error,
  ...input
}: FieldChrome & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId()
  return (
    <Chrome id={id} label={label} hint={hint} error={error}>
      <input
        id={id}
        className={styles.control}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        {...input}
      />
    </Chrome>
  )
}

export function TextArea({
  label,
  hint,
  error,
  ...textarea
}: FieldChrome & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId()
  return (
    <Chrome id={id} label={label} hint={hint} error={error}>
      <textarea
        id={id}
        className={`${styles.control} ${styles.textarea}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        {...textarea}
      />
    </Chrome>
  )
}

export function SelectField({
  label,
  hint,
  error,
  children,
  ...select
}: FieldChrome & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId()
  return (
    <Chrome id={id} label={label} hint={hint} error={error}>
      <select
        id={id}
        className={`${styles.control} ${styles.select}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        {...select}
      >
        {children}
      </select>
    </Chrome>
  )
}
