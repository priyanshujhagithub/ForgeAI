import { proposed, STEPS, stepIndex } from '../lib/runStatus'
import type { Run } from '../lib/types'
import styles from './StepTracker.module.css'

type StepState = 'done' | 'current' | 'stopped' | 'todo'

/**
 * Where a run is on its way from task to pull request. A run that ended
 * badly shows the step it stopped at, so the failure has a location.
 *
 * Every step keeps the same elements whatever its state, and the states differ
 * only in CSS. That is what lets a live run animate from one to the next: the
 * tick draws itself and the line to the next step fills, instead of the
 * markup being swapped.
 */
export function StepTracker({ run }: { run: Run }) {
  const reached = stepIndex(run.status, run.plan !== null, run.analysis !== null)
  const endedBadly = !run.active && !proposed(run.status)

  function stateOf(index: number): StepState {
    if (index < reached) return 'done'
    if (index > reached) return 'todo'
    return endedBadly ? 'stopped' : 'current'
  }

  return (
    <ol className={styles.tracker} aria-label="Run progress">
      {STEPS.map((step, index) => {
        const state = stateOf(index)
        return (
          <li
            key={step.status}
            className={`${styles.step} ${styles[state]}`}
            aria-current={state === 'current' ? 'step' : undefined}
          >
            <span className={styles.marker} aria-hidden="true">
              <span className={styles.number}>{index + 1}</span>
              <svg viewBox="0 0 16 16" className={styles.glyph}>
                <path className={styles.tick} pathLength="1" d="M3.5 8.5l3 3 6-7" />
                <path className={styles.cross} pathLength="1" d="M4.5 4.5l7 7m0-7l-7 7" />
              </svg>
            </span>
            <span className={styles.text}>
              <span className={styles.label}>{step.label}</span>
              <span className={styles.detail}>{step.detail}</span>
            </span>
            <span className="visually-hidden">
              {
                {
                  done: '(done)',
                  current: '(in progress)',
                  stopped: '(stopped here)',
                  todo: '(not started)',
                }[state]
              }
            </span>
          </li>
        )
      })}
    </ol>
  )
}
