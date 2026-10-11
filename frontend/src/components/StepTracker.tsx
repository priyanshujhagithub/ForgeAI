import { STEPS, stepIndex } from '../lib/runStatus'
import type { Run } from '../lib/types'
import styles from './StepTracker.module.css'

type StepState = 'done' | 'current' | 'stopped' | 'todo'

/**
 * Where a run is on its way from task to pull request. A run that ended
 * badly shows the step it stopped at, so the failure has a location.
 */
export function StepTracker({ run }: { run: Run }) {
  const reached = stepIndex(run.status, run.plan !== null, run.analysis !== null)
  const endedBadly = !run.active && run.status !== 'COMPLETED'

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
              {state === 'done' ? '✓' : state === 'stopped' ? '×' : index + 1}
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
