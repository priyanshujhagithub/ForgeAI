import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Button, ButtonLink } from '../components/Button'
import { Skeleton } from '../components/Feedback'
import { Icon } from '../components/Icon'
import { SelectField, TextField } from '../components/Field'
import { ErrorNotice, Notice } from '../components/Notice'
import { useConnection, useCreateProject, useRepositories } from '../lib/queries'
import shared from './shared.module.css'

export function NewProject() {
  const navigate = useNavigate()
  const connection = useConnection()
  const connected = connection.data?.connected === true
  const repositories = useRepositories(connected)
  const create = useCreateProject()

  const [repository, setRepository] = useState('')
  const [name, setName] = useState('')
  // Until the user types a name, it follows the chosen repository.
  const [nameEdited, setNameEdited] = useState(false)

  function chooseRepository(fullName: string) {
    setRepository(fullName)
    if (!nameEdited) setName(fullName.split('/')[1] ?? '')
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    create.mutate(
      { name: name.trim(), repository },
      {
        onSuccess: (project) =>
          navigate(`/app/projects/${project.id}`, { viewTransition: true }),
      },
    )
  }

  return (
    <div className={`${shared.stack} ${shared.narrow}`}>
      <Link to="/app" viewTransition className={shared.back}>
        <Icon name="arrowLeft" /> Projects
      </Link>

      <header>
        <h1 className={shared.pageTitle}>New project</h1>
        <p className={shared.pageLede}>
          Choose the repository ForgeAI should work on. It will read the code and open pull
          requests there; it never pushes to your default branch.
        </p>
      </header>

      {connection.isLoading && <Skeleton lines={3} />}

      {connection.data?.connected === false && (
        <Notice tone="warn" title="GitHub is not connected">
          <Link to="/app/settings" viewTransition>
            Connect your GitHub account
          </Link>{' '}
          to list your repositories.
        </Notice>
      )}

      {repositories.isError && (
        <ErrorNotice error={repositories.error} title="Could not list your repositories" />
      )}

      {connected && repositories.isLoading && <Skeleton lines={3} />}

      {repositories.data && (
        <form className={`${shared.form} enter`} onSubmit={onSubmit}>
          {create.isError && <ErrorNotice error={create.error} title="Could not create the project" />}

          <SelectField
            label="Repository"
            required
            value={repository}
            onChange={(event) => chooseRepository(event.target.value)}
            hint="The 100 repositories you updated most recently, including private ones."
          >
            <option value="" disabled>
              Choose a repository
            </option>
            {repositories.data.map((item) => (
              <option key={item.id} value={item.full_name}>
                {item.full_name}
                {item.private ? ' (private)' : ''}
              </option>
            ))}
          </SelectField>

          <TextField
            label="Project name"
            required
            maxLength={120}
            value={name}
            onChange={(event) => {
              setName(event.target.value)
              setNameEdited(true)
            }}
          />

          <div className={shared.formActions}>
            <Button
              type="submit"
              variant="primary"
              busy={create.isPending}
              disabled={!repository || !name.trim()}
            >
              Create project
            </Button>
            <ButtonLink to="/app" variant="quiet">
              Cancel
            </ButtonLink>
          </div>
        </form>
      )}
    </div>
  )
}
