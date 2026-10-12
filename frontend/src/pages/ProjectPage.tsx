import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, NavLink, useNavigate, useParams } from 'react-router'
import { Button } from '../components/Button'
import { EmptyState, RowSkeleton, Skeleton } from '../components/Feedback'
import { Icon } from '../components/Icon'
import { TextArea } from '../components/Field'
import { ErrorNotice, Notice } from '../components/Notice'
import { StatusPill } from '../components/StatusPill'
import { timeAgo } from '../lib/format'
import { nth } from '../lib/motion'
import { useDeleteProject, useProject, useRuns, useStartRun } from '../lib/queries'
import type { Run } from '../lib/types'
import { NotFound } from './NotFound'
import shared from './shared.module.css'
import styles from './ProjectPage.module.css'

const TASK_LIMIT = 2000

const EXAMPLES = [
  'Add GET /health that returns HTTP 200 with the JSON body {"status": "ok"}.',
  'Reject registration with HTTP 422 when the password is shorter than 8 characters.',
  'Store and return email addresses in lower case with surrounding whitespace removed.',
]

export function ProjectPage() {
  const projectId = Number(useParams().projectId)
  const project = useProject(projectId)
  const runs = useRuns(projectId)

  if (project.isLoading) return <Skeleton lines={5} />
  if (project.isError) return <ErrorNotice error={project.error} title="Could not load the project" />
  if (project.notFound || !project.data) return <NotFound what="project" />

  const { data } = project
  const activeRun = runs.data?.find((run) => run.active)

  return (
    <div className={shared.stack}>
      <Link to="/app" viewTransition className={shared.back}>
        <Icon name="arrowLeft" /> Projects
      </Link>

      <header>
        <p className="eyebrow">Project</p>
        <h1 className={`${shared.pageTitle} vt-project-title`}>{data.name}</h1>
        {data.githubRepoUrl && (
          <p className={shared.pageLede}>
            <a href={data.githubRepoUrl} target="_blank" rel="noreferrer" className="mono">
              {data.githubOwner}/{data.githubRepoName}
            </a>
          </p>
        )}
      </header>

      <div className={styles.columns}>
        <section aria-labelledby="start-heading" className={styles.compose}>
          <h2 id="start-heading" className={shared.sectionTitle}>
            Start a run
          </h2>
          {activeRun ? (
            <Notice tone="busy" title="A run is in progress">
              One run at a time per project.{' '}
              <Link to={`/app/runs/${activeRun.id}`} viewTransition>
                Watch the current run
              </Link>
              .
            </Notice>
          ) : (
            <TaskForm projectId={projectId} />
          )}
        </section>

        <section aria-labelledby="history-heading" className={styles.history}>
          <div className={shared.sectionHead}>
            <h2 id="history-heading" className={shared.sectionTitle}>
              Runs
            </h2>
            {runs.data && runs.data.length > 0 && (
              <span className="muted">{runs.data.length} total</span>
            )}
          </div>

          {runs.isLoading && <RowSkeleton rows={3} />}
          {runs.isError && <ErrorNotice error={runs.error} title="Could not load runs" />}
          {runs.data?.length === 0 && (
            <EmptyState title="No runs yet">
              Describe a small, specific change. Good tasks name the behaviour you want and how
              you would know it works.
            </EmptyState>
          )}
          {runs.data && runs.data.length > 0 && (
            <ul className={styles.runs}>
              {runs.data.map((run, index) => (
                // Fades without moving: the row holds a title shared with the run page.
                <li key={run.id} className="enter-fade" style={nth(index)}>
                  <RunRow run={run} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <DeleteProject projectId={projectId} disabled={Boolean(activeRun)} />
    </div>
  )
}

function TaskForm({ projectId }: { projectId: number }) {
  const navigate = useNavigate()
  const start = useStartRun(projectId)
  const [task, setTask] = useState('')

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    start.mutate(task.trim(), {
      onSuccess: (run) => navigate(`/app/runs/${run.id}`, { viewTransition: true }),
    })
  }

  return (
    <form className={shared.form} onSubmit={onSubmit}>
      {start.isError && <ErrorNotice error={start.error} title="Could not start the run" />}

      <TextArea
        label="Task"
        required
        maxLength={TASK_LIMIT}
        rows={5}
        value={task}
        placeholder="Describe one change, the way you would in a ticket."
        onChange={(event) => setTask(event.target.value)}
        hint={`${task.length} / ${TASK_LIMIT}`}
      />

      <div>
        <p className={styles.examplesLabel}>Or start from an example</p>
        <ul className={styles.examples}>
          {EXAMPLES.map((example) => (
            <li key={example}>
              <button type="button" className={styles.example} onClick={() => setTask(example)}>
                {example}
              </button>
            </li>
          ))}
        </ul>
      </div>

      <div className={shared.formActions}>
        <Button type="submit" variant="primary" busy={start.isPending} disabled={!task.trim()}>
          Start run
        </Button>
        <span className="muted">Takes a few minutes. You can leave this page.</span>
      </div>
    </form>
  )
}

function RunRow({ run }: { run: Run }) {
  const attempts = run.result?.attempts.length ?? 0

  return (
    <NavLink to={`/app/runs/${run.id}`} viewTransition className={styles.run}>
      {({ isTransitioning }) => (
        <>
          <span className={`${styles.runTask} ${isTransitioning ? 'vt-run-title' : ''}`}>
            {run.task}
          </span>
          <span className={styles.runMeta}>
            <StatusPill status={run.status} />
            <span>{timeAgo(run.createdAt)}</span>
            {attempts > 0 && (
              <span>
                {attempts} {attempts === 1 ? 'attempt' : 'attempts'}
              </span>
            )}
            {run.pullRequestNumber && <span>Pull request #{run.pullRequestNumber}</span>}
          </span>
          <Icon name="arrowRight" className={styles.runOpen} />
        </>
      )}
    </NavLink>
  )
}

/** Deleting takes two deliberate clicks, because it also removes the run history. */
function DeleteProject({ projectId, disabled }: { projectId: number; disabled: boolean }) {
  const navigate = useNavigate()
  const remove = useDeleteProject()
  const [confirming, setConfirming] = useState(false)

  return (
    <section className={styles.danger} aria-labelledby="delete-heading">
      <div>
        <h2 id="delete-heading" className={shared.sectionTitle}>
          Delete this project
        </h2>
        <p className="muted">
          Removes the project and its run history from ForgeAI. Your repository and any pull
          requests are not touched.
        </p>
        {remove.isError && <ErrorNotice error={remove.error} title="Could not delete" />}
      </div>
      {confirming ? (
        <div className={`${shared.formActions} enter-fade`}>
          <Button
            variant="danger"
            busy={remove.isPending}
            onClick={() =>
              remove.mutate(projectId, {
                onSuccess: () => navigate('/app', { viewTransition: true }),
              })
            }
          >
            Yes, delete it
          </Button>
          <Button variant="quiet" onClick={() => setConfirming(false)}>
            Keep it
          </Button>
        </div>
      ) : (
        <Button variant="danger" disabled={disabled} onClick={() => setConfirming(true)}>
          Delete project
        </Button>
      )}
    </section>
  )
}
