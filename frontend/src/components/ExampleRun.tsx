import { useEffect, useState } from 'react'
import { formatDuration } from '../lib/format'
import { usePrefersReducedMotion } from '../lib/motion'
import { Icon } from './Icon'
import { Pill } from './StatusPill'
import styles from './ExampleRun.module.css'

// The first run ForgeAI completed on this project's demo repository. The
// totals are the measured ones; the replay compresses four minutes into a few
// seconds, and `share` only sets the rhythm of that replay.
const TOTAL_SECONDS = 245
const SANDBOX = '15.6 s'

const STEPS = [
  { doing: 'Reading the repository', done: 'Read the repository at one commit', share: 0.2 },
  { doing: 'Planning the change', done: 'Planned the change', share: 0.2 },
  { doing: 'Writing the code', done: 'Wrote the code', share: 0.3 },
  { doing: 'Running your tests in a sandbox', done: 'Tests passed in the sandbox', share: 0.2 },
  { doing: 'Opening the pull request', done: 'Opened pull request #1', share: 0.1 },
]

// The point in the replay, from 0 to 1, at which each step finishes.
const FINISHES = STEPS.map((_, index) =>
  STEPS.slice(0, index + 1).reduce((sum, step) => sum + step.share, 0),
)

const PLAY_MS = 5600
const WAIT_MS = 900
const TICK_MS = 80

/**
 * How far through the replay we are, from 0 to 1. Round 0 is the first
 * showing, which plays only if `autoplay`; every later round is a replay.
 */
function usePlayback(round: number, autoplay: boolean, enabled: boolean): number {
  const [progress, setProgress] = useState(autoplay ? 0 : 1)

  useEffect(() => {
    if (!enabled || (round === 0 && !autoplay)) return
    const start = performance.now() + WAIT_MS
    const timer = setInterval(() => {
      const now = Math.min(1, Math.max(0, (performance.now() - start) / PLAY_MS))
      setProgress(now)
      if (now === 1) clearInterval(timer)
    }, TICK_MS)
    return () => clearInterval(timer)
  }, [round, autoplay, enabled])

  return enabled ? progress : 1
}

interface ExampleRunProps {
  /** Play the run when it first appears. Without this it starts out finished. */
  autoplay?: boolean
}

/** A real run, replayed the way the product shows one while it is in progress. */
export function ExampleRun({ autoplay = false }: ExampleRunProps) {
  const still = usePrefersReducedMotion()
  const [round, setRound] = useState(0)
  const at = usePlayback(round, autoplay, !still)

  const completed = FINISHES.filter((finish) => at >= finish - 1e-6).length
  const finished = completed === STEPS.length
  const tested = completed >= 4

  return (
    <figure className={`${styles.example} vt-example`}>
      <div className={styles.head}>
        <span className="mono">demo / registration-service</span>
        <Pill tone={finished ? 'ok' : 'busy'}>{finished ? 'Pull request open' : 'Running'}</Pill>
      </div>
      <div className={styles.progress} aria-hidden="true">
        <span style={{ transform: `scaleX(${at})` }} data-finished={finished || undefined} />
      </div>

      <p className={styles.task}>
        Add GET /health that returns HTTP 200 with the JSON body {'{"status": "ok"}'}.
      </p>

      <ol className={styles.steps}>
        {STEPS.map((step, index) => {
          const state = index < completed ? 'done' : index === completed ? 'doing' : 'todo'
          return (
            <li key={step.done} className={styles[state]}>
              <span className={styles.marker} aria-hidden="true">
                <svg viewBox="0 0 16 16">
                  <path pathLength="1" d="M3.5 8.5l3 3 6-7" />
                </svg>
              </span>
              {state === 'done' ? step.done : step.doing}
            </li>
          )
        })}
      </ol>

      <dl className={styles.meta}>
        <div>
          <dt>Total</dt>
          <dd>{formatDuration(TOTAL_SECONDS * at)}</dd>
        </div>
        <div>
          <dt>Sandbox</dt>
          <dd>{tested ? SANDBOX : '–'}</dd>
        </div>
        <div>
          <dt>Attempts</dt>
          <dd>{tested ? '1' : '–'}</dd>
        </div>
      </dl>

      <figcaption className={styles.caption}>
        <span>A real run on this project’s demo repository.</span>
        {!still && (
          <button
            type="button"
            className={styles.replay}
            disabled={!finished}
            onClick={() => setRound((current) => current + 1)}
          >
            <Icon name="replay" />
            Replay at speed
          </button>
        )}
      </figcaption>
    </figure>
  )
}
