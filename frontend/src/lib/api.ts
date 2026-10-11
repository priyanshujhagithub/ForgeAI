import { clearSession, readSession } from './session'
import type {
  GitHubConnection,
  Project,
  Repository,
  RepositoryFile,
  Run,
} from './types'

/** An error the API reported, carrying its HTTP status and its own explanation. */
export class ApiError extends Error {
  status: number

  constructor(status: number, detail: string) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
  }
}

interface RequestOptions {
  body?: unknown
  /** Login and registration are the only calls made without a token. */
  anonymous?: boolean
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const session = options.anonymous ? null : readSession()
  if (session) headers.Authorization = `Bearer ${session.token}`

  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the ForgeAI server. Check that it is running.')
  }

  if (response.status === 401 && !options.anonymous) {
    // The token expired or was rejected: sign out everywhere, and the router
    // sends the user to the login page.
    clearSession()
    throw new ApiError(401, 'Your session has ended. Sign in again.')
  }

  if (!response.ok) throw new ApiError(response.status, await errorDetail(response))

  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** The API reports errors as RFC 9457 problem documents with a `detail`. */
async function errorDetail(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { detail?: string }
    if (body.detail) return body.detail
  } catch {
    // Not JSON; fall through to a generic message.
  }
  return `The server answered with HTTP ${response.status}.`
}

const v1 = '/api/v1'

export const api = {
  login: (email: string, password: string) =>
    request<{ accessToken: string }>('POST', `${v1}/auth/login`, {
      body: { email, password },
      anonymous: true,
    }),

  register: (email: string, password: string) =>
    request<unknown>('POST', `${v1}/auth/register`, {
      body: { email, password },
      anonymous: true,
    }),

  connection: () => request<GitHubConnection>('GET', `${v1}/github/connection`),

  authorizationUrl: () =>
    request<{ authorizationUrl: string }>('GET', `${v1}/auth/github/url`).then(
      (body) => body.authorizationUrl,
    ),

  repositories: () => request<Repository[]>('GET', `${v1}/github/repositories`),

  projects: () => request<Project[]>('GET', `${v1}/projects`),

  createProject: (name: string, description: string) =>
    request<Project>('POST', `${v1}/projects`, { body: { name, description } }),

  connectRepository: (projectId: number, owner: string, repository: string) =>
    request<Project>('POST', `${v1}/projects/${projectId}/github`, {
      body: { owner, repository },
    }),

  deleteProject: (projectId: number) => request<void>('DELETE', `${v1}/projects/${projectId}`),

  startRun: (projectId: number, task: string) =>
    request<Run>('POST', `${v1}/projects/${projectId}/runs`, { body: { task } }),

  run: (runId: string) => request<Run>('GET', `${v1}/runs/${runId}`),

  runs: (projectId: number) => request<Run[]>('GET', `${v1}/projects/${projectId}/runs`),

  /** A file as it was at `ref` (a commit SHA), so a diff stays correct after the PR is merged. */
  file: (projectId: number, path: string, ref: string | null) => {
    const query = new URLSearchParams({ path })
    if (ref) query.set('ref', ref)
    return request<RepositoryFile>('GET', `${v1}/github/projects/${projectId}/file?${query}`)
  },
}
