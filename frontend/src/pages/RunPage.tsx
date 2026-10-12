import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ButtonAnchor } from '../components/Button'
import { DiffView } from '../components/DiffView'
import { Skeleton } from '../components/Feedback'
import { Icon } from '../components/Icon'
import { ErrorNotice, Notice } from '../components/Notice'
import { Pill, StatusPill } from '../components/StatusPill'
import { StepTracker } from '../components/StepTracker'
import { Tabs } from '../components/Tabs'
import { ApiError } from '../lib/api'
import {
  formatCost,
  formatDateTime,
  formatDuration,
  secondsBetween,
  shortSha,
} from '../lib/format'
import { nth } from '../lib/motion'
import { useProject, useRun } from '../lib/queries'
import { proposed, STATUS } from '../lib/runStatus'
import type { Analysis, Attempt, Plan, Run } from '../lib/types'
import { NotFound } from './NotFound'
import shared from './shared.module.css'
import styles from './RunPage.module.css'

type TabId = 'plan' | 'changes' | 'attempts' | 'analysis'

export function RunPage() {
  const runId = useParams().runId ?? ''
  const run = useRun(runId)
  const project = useProject(run.data?.projectId ?? -1)
  const [params, setParams] = useSearchParams()
  const finishedWhileWatching = useFinishedWhileWatching(runId, run.data?.active ?? false)

  if (run.isLoading) return <Skeleton lines={6} />
  if (run.error instanceof ApiError && run.error.status === 404) return <NotFound what="run" />
  if (run.isError) return <ErrorNotice error={run.error} title="Could not load the run" />
  if (!run.data) return null

  const data = run.data
  const changes = data.result?.finalChanges ?? []
  const attempts = data.result?.attempts ?? []

  const tabs = [
    { id: 'plan', label: 'Plan' },
    { id: 'changes', label: 'Changes', count: changes.length },
    { id: 'attempts', label: 'Test attempts', count: attempts.length },
    { id: 'analysis', label: 'Repository analysis' },
  ]
  const requested = params.get('tab')
  const selected = (tabs.some((tab) => tab.id === requested) ? requested : 'plan') as TabId

  return (
    <div className={shared.stack}>
      <Link to={`/app/projects/${data.projectId}`} viewTransition className={shared.back}>
        <Icon name="arrowLeft" /> {project.data?.name ?? 'Project'}
      </Link>

      <header className={styles.head}>
        <div className={styles.headTop}>
          <p className="eyebrow">Run</p>
          <StatusPill status={data.status} />
        </div>
        <h1 className={`${styles.task} vt-run-title`}>{data.task}</h1>
      </header>

      <Outcome run={data} celebrate={finishedWhileWatching && proposed(data.status)} />

      <section aria-label="Progress" className={shared.card}>
        <StepTracker run={data} />
      </section>

      <RunFacts run={data} repository={repositoryName(project.data)} attempts={attempts.length} />

      <Tabs
        label="Run details"
        tabs={tabs}
        selected={selected}
        // replace: switching tabs should not pile up entries in the back button.
        // preventScrollReset: nor should it throw you back to the top of the page.
        onSelect={(id) => setParams({ tab: id }, { replace: true, preventScrollReset: true })}
      >
        {selected === 'plan' && <PlanPanel plan={data.plan} active={data.active} />}
        {selected === 'changes' && <ChangesPanel run={data} />}
        {selected === 'attempts' && (
          <AttemptsPanel
            attempts={attempts}
            active={data.active}
            noTests={data.result?.outcome === 'no_tests'}
          />
        )}
        {selected === 'analysis' && <AnalysisPanel analysis={data.analysis} active={data.active} />}
      </Tabs>
    </div>
  )
}

function repositoryName(project: { githubOwner: string | null; githubRepoName: string | null } | undefined) {
  return project?.githubOwner ? `${project.githubOwner}/${project.githubRepoName}` : null
}

/**
 * True once a run that was seen in progress on this page has finished: the
 * moment worth marking. Opening a run that had already finished is not one.
 */
function useFinishedWhileWatching(runId: string, active: boolean): boolean {
  const [watched, setWatched] = useState<string | null>(null)
  if (active && watched !== runId) setWatched(runId)
  return watched === runId && !active
}

/** The headline result of a finished run, and the way out to the pull request. */
function Outcome({ run, celebrate }: { run: Run; celebrate: boolean }) {
  if (run.active) {
    return (
      <Notice tone="busy" title="In progress" role="status">
        This page updates on its own. With a local model a run usually takes a few minutes; you
        can leave and come back.
      </Notice>
    )
  }

  const { tone, summary } = STATUS[run.status]
  return (
    <div className={`${styles.outcome} ${celebrate ? styles.celebrate : ''}`}>
      <Notice tone={tone} title={summary} role="status">
        {/* For an untested run the summary already says why; do not repeat it. */}
        {run.status === 'COMPLETED_UNTESTED' ? null : run.errorMessage}
      </Notice>
      {run.pullRequestUrl && (
        <ButtonAnchor
          href={run.pullRequestUrl}
          variant="primary"
          size="lg"
          className={celebrate ? 'enter' : undefined}
        >
          {run.status === 'COMPLETED_UNTESTED' ? 'Check' : 'Review'} pull request #
          {run.pullRequestNumber}
        </ButtonAnchor>
      )}
    </div>
  )
}

function RunFacts({
  run,
  repository,
  attempts,
}: {
  run: Run
  repository: string | null
  attempts: number
}) {
  const now = useNow(run.active)
  const elapsed = secondsBetween(run.createdAt, run.finishedAt, now)

  return (
    <dl className={shared.meta}>
      {repository && (
        <div>
          <dt>Repository</dt>
          <dd className="mono">{repository}</dd>
        </div>
      )}
      {run.baseSha && (
        <div>
          <dt>Started from</dt>
          <dd className="mono">
            {run.baseBranch} @ {shortSha(run.baseSha)}
          </dd>
        </div>
      )}
      <div>
        <dt>Started</dt>
        <dd>{formatDateTime(run.createdAt)}</dd>
      </div>
      <div>
        <dt>{run.active ? 'Elapsed' : 'Took'}</dt>
        {/* Digits of equal width, so the running clock does not jitter */}
        <dd className="mono tabular">{formatDuration(elapsed)}</dd>
      </div>
      {attempts > 0 && (
        <div>
          <dt>Test attempts</dt>
          <dd className="mono">{attempts} of 3</dd>
        </div>
      )}
      {run.sandboxSeconds !== null && run.sandboxSeconds > 0 && (
        <div>
          <dt>Sandbox</dt>
          <dd className="mono">
            {run.sandboxSeconds.toFixed(1)} s
            {run.estimatedCostUsd !== null && ` · est. ${formatCost(run.estimatedCostUsd)}`}
          </dd>
        </div>
      )}
    </dl>
  )
}

/** The current time, refreshed every second while `ticking`, for a live elapsed counter. */
function useNow(ticking: boolean): Date {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    if (!ticking) return
    const timer = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(timer)
  }, [ticking])
  return now
}

function Pending({ active, what }: { active: boolean; what: string }) {
  return active ? (
    <div>
      <p className="muted">Waiting for {what}…</p>
      <Skeleton lines={3} />
    </div>
  ) : (
    <p className="muted">The run ended before producing {what}.</p>
  )
}

function PlanPanel({ plan, active }: { plan: Plan | null; active: boolean }) {
  if (!plan) return <Pending active={active} what="the plan" />

  return (
    <div className={shared.prose}>
      <p className={styles.understanding}>{plan.taskUnderstanding}</p>
      <ol className={styles.planSteps}>
        {plan.steps.map((step, index) => (
          <li key={step.order} className={`${styles.planStep} enter`} style={nth(index)}>
            <span className={styles.planNumber} aria-hidden="true">
              {String(step.order).padStart(2, '0')}
            </span>
            <div>
              <p>{step.description}</p>
              <ul className={shared.tagList} aria-label="Files in this step">
                {step.files.map((file) => (
                  <li key={file} className={shared.tag}>
                    {file}
                  </li>
                ))}
              </ul>
            </div>
          </li>
        ))}
      </ol>
    </div>
  )
}

function ChangesPanel({ run }: { run: Run }) {
  const changes = run.result?.finalChanges ?? []
  if (changes.length === 0) return <Pending active={run.active} what="any changes" />

  return (
    <div className={styles.changes}>
      {!proposed(run.status) && !run.active && (
        <Notice tone="warn" title="These changes were not proposed">
          They are the last version the agent produced. They did not pass the tests, so no pull
          request was opened.
        </Notice>
      )}
      {changes.map((change, index) => (
        <div key={change.path} className="enter" style={nth(index)}>
          <DiffView projectId={run.projectId} change={change} baseSha={run.baseSha} />
        </div>
      ))}
    </div>
  )
}

function AttemptsPanel({
  attempts,
  active,
  noTests,
}: {
  attempts: Attempt[]
  active: boolean
  noTests: boolean
}) {
  if (attempts.length === 0 && noTests) {
    return (
      <p className="muted">
        No tests were run. This repository has no test command or test files, so there was
        nothing to check the change against.
      </p>
    )
  }
  if (attempts.length === 0) return <Pending active={active} what="a test run" />

  return (
    <ol className={styles.attempts}>
      {attempts.map((attempt, index) => (
        <li key={attempt.number} className="enter" style={nth(index)}>
          <AttemptCard attempt={attempt} />
        </li>
      ))}
    </ol>
  )
}

const OUTCOME = {
  passed: { label: 'Tests passed', tone: 'ok' },
  tests_failed: { label: 'Tests failed', tone: 'bad' },
  no_tests: { label: 'No tests found', tone: 'warn' },
  infrastructure_error: { label: 'Sandbox error', tone: 'warn' },
} as const

function AttemptCard({ attempt }: { attempt: Attempt }) {
  const sandbox = attempt.sandboxResult
  const outcome = OUTCOME[sandbox.outcome]
  const output = [sandbox.stdout, sandbox.stderr].filter((part) => part.trim()).join('\n')

  return (
    <article className={styles.attempt}>
      <header className={styles.attemptHead}>
        <h3 className={styles.attemptTitle}>Attempt {attempt.number}</h3>
        <Pill tone={outcome.tone}>{outcome.label}</Pill>
        <span className={styles.attemptFacts}>
          {sandbox.timedOut ? 'timed out' : `exit ${sandbox.exitCode}`} ·{' '}
          {sandbox.durationSeconds.toFixed(1)} s
        </span>
      </header>

      {attempt.diagnosis && (
        <dl className={styles.diagnosis}>
          <div>
            <dt>Root cause</dt>
            <dd>{attempt.diagnosis.rootCause}</dd>
          </div>
          <div>
            <dt>Fix</dt>
            <dd>{attempt.diagnosis.fixStrategy}</dd>
          </div>
          <div>
            <dt>Files to change</dt>
            <dd>
              <ul className={shared.tagList}>
                {attempt.diagnosis.filesToChange.map((file) => (
                  <li key={file} className={shared.tag}>
                    {file}
                  </li>
                ))}
              </ul>
            </dd>
          </div>
        </dl>
      )}

      {/* Failed output is what you came for; passing output can stay folded. */}
      <details className={styles.output} open={sandbox.outcome !== 'passed'}>
        <summary>
          <Icon name="chevronDown" />
          <span>
            Output of <code>{sandbox.command}</code>
          </span>
        </summary>
        <pre tabIndex={0}>{output || 'No output.'}</pre>
      </details>
    </article>
  )
}

function AnalysisPanel({ analysis, active }: { analysis: Analysis | null; active: boolean }) {
  if (!analysis) return <Pending active={active} what="the analysis" />

  return (
    <div className={shared.prose}>
      <p className={styles.understanding}>{analysis.summary}</p>
      <dl className={styles.analysis}>
        <div>
          <dt>Project type</dt>
          <dd>{analysis.projectType}</dd>
        </div>
        <div>
          <dt>Architecture</dt>
          <dd className={styles.preserve}>{analysis.architectureNotes}</dd>
        </div>
        <TagRow label="Key components" items={analysis.keyComponents} />
        <TagRow label="Dependencies" items={analysis.dependencies} />
        <TagRow label="Most relevant files" items={analysis.relevantFiles} />
      </dl>
    </div>
  )
}

function TagRow({ label, items }: { label: string; items: string[] }) {
  if (items.length === 0) return null
  return (
    <div>
      <dt>{label}</dt>
      <dd>
        <ul className={shared.tagList}>
          {items.map((item) => (
            <li key={item} className={shared.tag}>
              {item}
            </li>
          ))}
        </ul>
      </dd>
    </div>
  )
}
