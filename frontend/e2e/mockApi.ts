import type { Page, Route } from '@playwright/test'

// A stand-in for the Java API with realistic data, so browser tests can render
// every page without a backend.

/** A token shaped like the API's JWT. Only its claims are read by the app. */
export function fakeToken(email = 'ada@example.com'): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url')
  const exp = Math.floor(Date.now() / 1000) + 3600
  return `${encode({ alg: 'HS512' })}.${encode({ sub: '3', email, role: 'DEVELOPER', exp })}.sig`
}

const minutesAgo = (minutes: number) =>
  new Date(Date.now() - minutes * 60_000).toISOString().replace('Z', '')

const ORIGINAL_MAIN = `from fastapi import FastAPI

from .models import RegisterRequest, UserResponse
from .store import UserStore

app = FastAPI(title="Registration Service")
store = UserStore()


@app.post("/register", status_code=201, response_model=UserResponse)
def register(request: RegisterRequest):
    user = store.add(request.email, request.password)
    return UserResponse(id=user.id, email=user.email)


@app.get("/users")
def list_users():
    return [user.email for user in store.all()]
`

const NEW_MAIN = ORIGINAL_MAIN.replace(
  '    user = store.add(request.email, request.password)',
  '    user = store.add(request.email.strip().lower(), request.password)',
)

const NEW_TEST = `from fastapi.testclient import TestClient

from app.main import app, store


def test_email_is_stored_in_lower_case():
    store.clear()
    client = TestClient(app)

    response = client.post(
        "/register", json={"email": "  Ada@Example.COM ", "password": "correct-horse"}
    )

    assert response.status_code == 201
    assert response.json()["email"] == "ada@example.com"
`

const analysis = {
  summary:
    'A small FastAPI service that registers users and keeps them in memory. It exposes two endpoints and is covered by a pytest suite.',
  projectType: 'Python FastAPI service',
  keyComponents: ['FastAPI app', 'RegisterRequest model', 'UserStore'],
  dependencies: ['fastapi', 'httpx', 'pytest'],
  architectureNotes:
    'app/main.py defines the routes and delegates storage to UserStore in app/store.py. Request and response shapes live in app/models.py.',
  relevantFiles: ['app/main.py', 'app/models.py', 'app/store.py', 'tests/test_register.py'],
}

const plan = {
  taskUnderstanding:
    'Normalise the email address before it is stored, so the stored and returned value is trimmed and lower case.',
  filesToModify: ['app/main.py'],
  filesToCreate: ['tests/test_normalise.py'],
  steps: [
    {
      order: 1,
      description: 'Trim and lower-case the email in the register endpoint before storing it.',
      files: ['app/main.py'],
    },
    {
      order: 2,
      description: 'Add a test that registers a mixed-case, padded email and checks the result.',
      files: ['tests/test_normalise.py'],
    },
  ],
}

const finalChanges = [
  {
    path: 'app/main.py',
    action: 'modify',
    content: NEW_MAIN,
    explanation: 'Normalise the email before it reaches the store.',
  },
  {
    path: 'tests/test_normalise.py',
    action: 'create',
    content: NEW_TEST,
    explanation: 'Cover the new behaviour with a mixed-case, padded address.',
  },
]

const failedOutput = `F...                                                                     [100%]
=================================== FAILURES ===================================
______________________ test_email_is_stored_in_lower_case ______________________

    def test_email_is_stored_in_lower_case():
        store.clear()
        client = TestClient(app)
        response = client.post(
            "/register", json={"email": "  Ada@Example.COM ", "password": "correct-horse"}
        )
>       assert response.json()["email"] == "ada@example.com"
E       AssertionError: assert '  ada@example.com ' == 'ada@example.com'

tests/test_normalise.py:13: AssertionError
=========================== short test summary info ============================
FAILED tests/test_normalise.py::test_email_is_stored_in_lower_case
1 failed, 3 passed in 0.41s`

const baseRun = {
  projectId: 5,
  analysis,
  plan,
  baseBranch: 'main',
  baseSha: '0d0c23adcea4468b3e35e091147c75660c3340a7',
  errorMessage: null,
}

export const completedRun = {
  ...baseRun,
  id: '841a7bc5-0000-4000-8000-000000000001',
  task: 'Store and return email addresses in lower case with surrounding whitespace removed.',
  status: 'COMPLETED',
  active: false,
  result: {
    outcome: 'passed',
    finalChanges,
    attempts: [
      {
        number: 1,
        diagnosis: {
          rootCause:
            'The email is lower-cased but the surrounding whitespace is kept, so the stored value still has leading and trailing spaces.',
          fixStrategy: 'Call strip() on the email before lower-casing it.',
          filesToChange: ['app/main.py'],
        },
        changes: finalChanges,
        sandboxResult: {
          outcome: 'tests_failed',
          command: 'python -m pytest -q --disable-warnings',
          exitCode: 1,
          stdout: failedOutput,
          stderr: '',
          durationSeconds: 1.2,
          timedOut: false,
          message: null,
        },
      },
      {
        number: 2,
        diagnosis: null,
        changes: finalChanges,
        sandboxResult: {
          outcome: 'passed',
          command: 'python -m pytest -q --disable-warnings',
          exitCode: 0,
          stdout: '....                                                                     [100%]\n4 passed in 0.38s',
          stderr: '',
          durationSeconds: 1.0,
          timedOut: false,
          message: null,
        },
      },
    ],
    sandboxSeconds: 30.9,
    estimatedCostUsd: 0.00093,
    message: null,
  },
  branchName: 'forgeai/task-841a7bc5',
  pullRequestNumber: 2,
  pullRequestUrl: 'https://github.com/octocat/registration-service/pull/2',
  sandboxSeconds: 30.9,
  estimatedCostUsd: 0.00093,
  createdAt: minutesAgo(42),
  updatedAt: minutesAgo(33),
  finishedAt: minutesAgo(33),
}

export const failedRun = {
  ...baseRun,
  id: '2f6c1d90-0000-4000-8000-000000000002',
  task: 'Reject registration with HTTP 409 when the email address is already registered.',
  status: 'TESTS_FAILED',
  active: false,
  result: {
    ...completedRun.result,
    outcome: 'tests_failed',
    attempts: [completedRun.result.attempts[0]],
    message: 'Tests still failing after 3 attempts.',
  },
  branchName: null,
  pullRequestNumber: null,
  pullRequestUrl: null,
  errorMessage: 'Tests still failing after 3 attempts.',
  sandboxSeconds: 44.1,
  estimatedCostUsd: 0.00132,
  createdAt: minutesAgo(190),
  updatedAt: minutesAgo(178),
  finishedAt: minutesAgo(178),
}

export const untestedRun = {
  ...baseRun,
  id: '5c7d2e11-0000-4000-8000-000000000004',
  task: 'Add a README that explains what this project does.',
  status: 'COMPLETED_UNTESTED',
  active: false,
  result: {
    outcome: 'no_tests',
    finalChanges: [
      {
        path: 'NOTES.md',
        action: 'create',
        content: '# Registration service\n\nRegisters users and keeps them in memory.\n',
        explanation: 'Describe the project.',
      },
    ],
    attempts: [],
    sandboxSeconds: 0,
    estimatedCostUsd: 0,
    message: 'This repository has no tests, so the change could not be verified.',
  },
  branchName: 'forgeai/task-5c7d2e11',
  pullRequestNumber: 3,
  pullRequestUrl: 'https://github.com/octocat/registration-service/pull/3',
  errorMessage: 'This repository has no tests, so the change could not be verified.',
  sandboxSeconds: 0,
  estimatedCostUsd: 0,
  createdAt: minutesAgo(12),
  updatedAt: minutesAgo(9),
  finishedAt: minutesAgo(9),
}

export const activeRun = {
  ...baseRun,
  id: '9a0e44b2-0000-4000-8000-000000000003',
  task: 'Add GET /users/count that returns the number of registered users.',
  status: 'GENERATING_AND_TESTING',
  active: true,
  result: null,
  branchName: null,
  pullRequestNumber: null,
  pullRequestUrl: null,
  sandboxSeconds: null,
  estimatedCostUsd: null,
  createdAt: minutesAgo(3),
  updatedAt: minutesAgo(1),
  finishedAt: null,
}

const projects = [
  {
    id: 5,
    name: 'Registration service',
    description: 'octocat/registration-service',
    githubOwner: 'octocat',
    githubRepoName: 'registration-service',
    githubRepoUrl: 'https://github.com/octocat/registration-service',
    createdAt: minutesAgo(3000),
    updatedAt: minutesAgo(3000),
  },
  {
    id: 6,
    name: 'Billing API',
    description: 'octocat/billing-api',
    githubOwner: 'octocat',
    githubRepoName: 'billing-api',
    githubRepoUrl: 'https://github.com/octocat/billing-api',
    createdAt: minutesAgo(900),
    updatedAt: minutesAgo(900),
  },
]

const repositories = [
  { id: 1, name: 'registration-service', full_name: 'octocat/registration-service', html_url: '', private: false },
  { id: 2, name: 'billing-api', full_name: 'octocat/billing-api', html_url: '', private: true },
  { id: 3, name: 'docs', full_name: 'octocat/docs', html_url: '', private: false },
]

interface MockOptions {
  connected?: boolean
  /** Runs returned for project 5, newest first. */
  runs?: object[]
  emptyProjects?: boolean
}

export async function mockApi(page: Page, options: MockOptions = {}) {
  const { connected = true, runs = [completedRun, failedRun], emptyProjects = false } = options
  const allRuns = [completedRun, failedRun, activeRun, untestedRun]

  const json = (route: Route, body: unknown, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })

  await page.route('**/api/v1/**', (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()

    if (path === '/auth/login') {
      const { password } = request.postDataJSON() as { password: string }
      return password === 'wrong-password'
        ? json(route, { status: 401, detail: 'Invalid email or password' }, 401)
        : json(route, { accessToken: fakeToken() })
    }
    if (path === '/auth/register') return json(route, { id: 3 })
    if (path === '/github/connection') {
      return json(route, { connected, githubUsername: connected ? 'octocat' : null })
    }
    if (path === '/github/repositories') return json(route, repositories)
    if (path === '/projects' && method === 'GET') return json(route, emptyProjects ? [] : projects)
    if (path === '/projects' && method === 'POST') return json(route, { ...projects[0], id: 7 })
    if (path === '/projects/7/github') return json(route, { ...projects[0], id: 7 })
    if (path === '/projects/5/runs' && method === 'GET') return json(route, runs)
    if (path === '/projects/5/runs' && method === 'POST') return json(route, activeRun, 202)
    if (/^\/projects\/\d+\/runs$/.test(path)) return json(route, [])

    const run = allRuns.find((candidate) => path === `/runs/${candidate.id}`)
    if (run) return json(route, run)
    if (path.startsWith('/runs/')) return json(route, { status: 404, detail: 'Run not found' }, 404)

    if (path === '/github/projects/5/file') {
      return json(route, { path: url.searchParams.get('path'), content: ORIGINAL_MAIN, sha: 'abc' })
    }
    return json(route, { status: 404, detail: `No mock for ${method} ${path}` }, 404)
  })
}

export async function signIn(page: Page) {
  const token = fakeToken()
  await page.addInitScript((value) => localStorage.setItem('forgeai.token', value), token)
}
