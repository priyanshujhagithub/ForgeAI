import { Link, NavLink } from 'react-router'
import { Button, ButtonLink } from '../components/Button'
import { CardSkeleton, EmptyState } from '../components/Feedback'
import { Icon } from '../components/Icon'
import { ErrorNotice, Notice } from '../components/Notice'
import { StatusPill } from '../components/StatusPill'
import { timeAgo } from '../lib/format'
import { nth } from '../lib/motion'
import { useConnection, useDeleteProject, useProjects, useRuns } from '../lib/queries'
import type { Project } from '../lib/types'
import shared from './shared.module.css'
import styles from './Projects.module.css'

export function Projects() {
  const projects = useProjects()
  const connection = useConnection()
  const needsGitHub = connection.data?.connected === false

  return (
    <div className={shared.stack}>
      <header className={shared.pageHead}>
        <div>
          <h1 className={shared.pageTitle}>Projects</h1>
          <p className={shared.pageLede}>
            A project is one GitHub repository. Open one to give it a task.
          </p>
        </div>
        {!needsGitHub && (
          <ButtonLink to="/app/projects/new" variant="primary">
            New project
          </ButtonLink>
        )}
      </header>

      {needsGitHub && (
        <Notice tone="warn" title="Connect GitHub first">
          ForgeAI needs access to your repositories to read code and open pull requests.{' '}
          <Link to="/app/settings" viewTransition>
            Connect in Settings
          </Link>
          .
        </Notice>
      )}

      {projects.isLoading && (
        <ul className={styles.grid}>
          {[0, 1, 2].map((index) => (
            <li key={index}>
              <CardSkeleton />
            </li>
          ))}
        </ul>
      )}
      {projects.isError && <ErrorNotice error={projects.error} title="Could not load projects" />}

      {projects.data?.length === 0 && !needsGitHub && (
        <EmptyState
          title="No projects yet"
          action={
            <ButtonLink to="/app/projects/new" variant="primary">
              Create your first project
            </ButtonLink>
          }
        >
          Pick one of your repositories and ForgeAI will be ready to take a task for it.
        </EmptyState>
      )}

      {projects.data && projects.data.length > 0 && (
        <ul className={styles.grid}>
          {projects.data.map((project, index) => (
            // Fades without moving: the card holds a title shared with the project page.
            <li key={project.id} className="enter-fade" style={nth(index)}>
              {project.githubRepoName ? (
                <ProjectCard project={project} />
              ) : (
                <UnlinkedProject project={project} />
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function ProjectCard({ project }: { project: Project }) {
  const runs = useRuns(project.id)
  const latest = runs.data?.[0]

  return (
    <NavLink to={`/app/projects/${project.id}`} viewTransition className={styles.card}>
      {({ isTransitioning }) => (
        <>
          <span className={styles.repo}>
            {project.githubOwner}/{project.githubRepoName}
          </span>
          <Icon name="arrowRight" className={styles.open} />
          {/* Only the card being opened shares its title with the next page. */}
          <span className={`${styles.name} ${isTransitioning ? 'vt-project-title' : ''}`}>
            {project.name}
          </span>
          <span className={styles.latest}>
            {latest ? (
              <>
                <StatusPill status={latest.status} />
                <span className={styles.when}>{timeAgo(latest.createdAt)}</span>
              </>
            ) : (
              <span className={styles.when}>{runs.isLoading ? ' ' : 'No runs yet'}</span>
            )}
          </span>
        </>
      )}
    </NavLink>
  )
}

/** A project whose repository was never linked. It cannot run anything, so offer to remove it. */
function UnlinkedProject({ project }: { project: Project }) {
  const remove = useDeleteProject()

  return (
    <div className={`${styles.card} ${styles.unlinked}`}>
      <span className={styles.repo}>No repository</span>
      <span className={styles.name}>{project.name}</span>
      <span className={styles.latest}>
        <Button
          variant="danger"
          busy={remove.isPending}
          onClick={() => remove.mutate(project.id)}
        >
          Delete
        </Button>
      </span>
      {remove.isError && <ErrorNotice error={remove.error} title="Could not delete" />}
    </div>
  )
}
