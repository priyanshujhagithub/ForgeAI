# ForgeAI

ForgeAI takes a GitHub repository and a task written in plain English, and
works towards a tested pull request: it analyses the repository, plans the
change, writes the code, runs the repository's tests in an isolated sandbox,
tries to fix what fails, and opens a pull request for a human to review.

It is a learning project built to be read: two small services, no agent
framework, and tests at every layer.

```mermaid
flowchart LR
    UI[React web app] --> J[project-service<br/>Java, Spring Boot]
    J <--> DB[(PostgreSQL)]
    J <--> GH[GitHub API]
    J --> P[ai-service<br/>Python, FastAPI]
    P --> M[Model<br/>Ollama or any OpenAI-compatible API]
    P --> S[E2B sandbox]
    P -.-> L[Langfuse traces]
```

- **project-service** owns users, authentication, GitHub access, the record of
  each run, and the pull request. It never calls a model.
- **ai-service** owns every prompt, model call and sandbox run. It holds no
  state and no user credentials.

How a run moves through the system, and why it is built this way, is in
[docs/architecture.md](docs/architecture.md).

## What works today

| Stage | State |
|---|---|
| Sign-up, login (JWT), project ownership | Working, tested |
| GitHub OAuth, reading a repository at a fixed commit | Working, tested |
| Repository analysis, task planning, code generation | Working against a real local model |
| Running tests in an E2B sandbox | Working against real E2B |
| Diagnose-and-fix loop (up to 3 attempts) | Working; see results below for how often it succeeds |
| Langfuse tracing | Working against Langfuse Cloud |
| Background runs with a pollable status | Working: a real run went from task to pull request in 4 min 05 s |
| Opening the pull request | Working: opened (and the owner merged) a real pull request on the demo repository |
| React web app | Tested in a real browser at desktop and phone sizes; see [Frontend](#frontend) |
| Maven and Gradle repositories | Not supported yet (needs a sandbox image with a JDK) |

## Results

Everything below was measured on one laptop (Intel i5-11400H, 16 GB RAM,
GTX 1650 with 4 GB VRAM) running `qwen3:8b` through Ollama, which on this
hardware produces about 4 to 5 tokens per second.

| Step | Typical time |
|---|---|
| Repository analysis | about 60 s |
| Engineering plan | about 75 s |
| A single-file task, start to finish | about 2.5 min |
| A three-file task with correction attempts | 12 to 13 min |
| Sandbox setup (clone and install) | about 12 s |
| One test run in the sandbox | about 1 s |
| E2B credit per run (estimated) | $0.001 to $0.002 |

### Evaluation: 3 of 8 tasks solved

Eight tasks were run against the [demo repository](demo/registration-service)
with the evaluation harness. A task is solved only if the agent's own tests
pass and a hidden acceptance test, which the agent never sees, also passes.

| Task | Agent's own tests | Attempts | Hidden test | Solved | Time |
|---|---|---|---|---|---|
| Add `GET /health` | passed | 1 | passed | yes | 148 s |
| Add `GET /users/count` | passed | 1 | passed | yes | 150 s |
| Normalise stored emails | passed | 2 | passed | yes | 550 s |
| Reject short passwords | passed | 1 | **failed** | no | 165 s |
| Reject duplicate emails | failed | 1 | not run | no | 520 s |
| Add `GET /users/{id}` | failed | 1 | not run | no | 402 s |
| Add `DELETE /users/{id}` | failed | 3 | not run | no | 645 s |
| Reject invalid emails | no valid output | 0 | not run | no | 130 s |

All eight together took 45 minutes and 235 seconds of sandbox time, an
estimated $0.007 of E2B credit. Raw results are in
[services/ai-service/evals/results](services/ai-service/evals/results).

What the failures show:

- **The hidden test matters.** "Reject short passwords" passed the tests the
  agent wrote for itself and still had the behaviour wrong.
- **Self-correction works when the model can act on a diagnosis.** "Normalise
  stored emails" failed its first test run and passed on the second attempt.
- **The most common failure is the model returning a file unchanged** when
  asked to write or fix it (three of the five failures). The pipeline detects
  this and stops instead of looping, but cannot make the model do the work.
- A harder task that changes three files at once ("validate email and
  password, with tests") was run end to end three times and did not pass
  within three attempts in any of them. Each run failed differently: a missing
  dependency, a test fixture lost in a rewrite, a syntax error.

In the full flow a pull request is only opened when the agent's tests pass, so
four of these five failures would have stopped before reaching a repository.
The fifth, "Reject short passwords", would have become a pull request with the
wrong behaviour. That is why the last step is a human review, not a merge.

The model is the limiting factor here, and it is swappable: any
OpenAI-compatible endpoint can be used by changing three environment variables.

## Design points worth a look

- **Model output is never trusted.** Every reply is validated against a schema
  and against the real repository (paths must exist, new files must be new,
  nothing may escape the repository or touch CI workflows). Invalid replies get
  one repair attempt with the specific errors.
- **The plan decides where code goes.** During generation the model supplies
  file content only; it cannot choose a path or delete a file.
- **Diagnose, then fix.** A failed test run produces a structured root cause
  first, and only the files it names are rewritten.
- **Generated code runs only in a sandbox**, and only changes whose tests
  passed are proposed, as a pull request on a new branch.
- **Runs are asynchronous and durable.** `POST /runs` returns `202`; progress is
  a database row. A partial unique index guarantees one active run per project
  even under concurrent requests.
- **Secrets.** GitHub tokens are encrypted at rest with AES-256-GCM and leave
  the Java service only for private clones. No secret lives in the repository.
- **Costs are bounded.** Sandboxes have a hard lifetime and are always
  destroyed; each run records sandbox seconds and an estimated credit cost.
  The whole stack runs on free tiers.

## Run it locally

Prerequisites: JDK 21+, Python 3.13, Docker, and [Ollama](https://ollama.com).
Free accounts on [E2B](https://e2b.dev) and [Langfuse](https://langfuse.com)
are optional; without them, sandbox runs report "unavailable" and tracing is off.

```bash
# 1. Secrets
cp .env.example .env          # then fill it in; the file explains each value

# 2. Model (once)
ollama pull qwen3:8b
ollama create forgeai-qwen3 -f infrastructure/ollama/Modelfile

# 3. Database and Java API (http://localhost:8080)
docker compose up -d postgres
./start-forgeai.sh

# 4. AI service (http://localhost:8001), in a second terminal
cd services/ai-service
python -m venv .venv && source .venv/Scripts/activate   # bin/activate on Linux/macOS
pip install -r requirements-dev.txt
python -m uvicorn app.main:app --port 8001

# 5. Web app (http://localhost:5180), in a third terminal
cd frontend
npm install
npm run dev
```

In the web app: create an account, connect GitHub in Settings, create a project
for a repository you own, type a task and start a run.

To use a different model, set `LLM_BASE_URL`, `LLM_API_KEY` and `LLM_MODEL` in
`.env`. Any OpenAI-compatible endpoint works; no code changes are needed.

Both services can also run in containers: `docker compose --profile app up --build`.

## API

All endpoints except register, login and the OAuth callback need
`Authorization: Bearer <token>`. Errors are RFC 9457 problem documents.

| Method and path | Purpose |
|---|---|
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | Create an account, get a token |
| `GET /api/v1/auth/github/url` | URL that starts the GitHub OAuth flow |
| `POST /api/v1/projects`, `POST /api/v1/projects/{id}/github` | Create a project, link a repository |
| `POST /api/v1/projects/{id}/runs` | Start a run; answers `202` with a `Location` to poll |
| `GET /api/v1/runs/{runId}` | Status, analysis, plan, changes, attempts, pull request |
| `GET /api/v1/projects/{id}/runs` | Run history |
| `GET /api/v1/github/projects/{id}/ai-analysis` | Analysis only |
| `POST /api/v1/github/projects/{id}/plan` | Plan only |
| `POST /api/v1/github/projects/{id}/generate` | Generate changes for a plan, without testing |

## Tests

| Suite | Tests | What it needs |
|---|---|---|
| `services/project-service` (`./mvnw verify`) | 110 | Docker, for a throwaway PostgreSQL |
| `services/ai-service` (`pytest`) | 138 | Nothing: the model, E2B and Langfuse are faked |
| `frontend` (`npm test`) | 30 | Nothing: logic and components in jsdom |
| `frontend` (`npm run test:e2e`) | 25, each at desktop and phone size | Chromium; the API is mocked |

CI runs all three on every push without any secrets.

To measure the agent itself, the evaluation harness runs real tasks against a
real repository. A task counts as solved only if the agent's own tests pass
**and** a hidden acceptance test, which the agent never sees, also passes:

```bash
cd services/ai-service
python -m evals.run --repo <owner>/<name> --max-tasks 2
```

## Frontend

The web app in [frontend/](frontend/) is React and TypeScript on Vite, with React
Router and TanStack Query. It has no UI kit and no CSS framework: the look is a
small set of design tokens and CSS Modules, with light and dark themes.

- **Pages:** landing, sign in, create account, projects, new project, project
  (start a run, history), run (progress, plan, diff, test attempts, analysis),
  settings, and a not-found page.
- **View Transitions API:** navigation cross-fades, the header stays in place,
  and the title of the project or run you open travels from the list into the
  page heading. Browsers without the API just navigate.
- **Responsive:** written phone-first. A browser test loads every page at
  390 px and fails if any of them scrolls sideways; wide content such as diffs
  and test output scrolls inside its own box.
- **Accessible:** real labels and error messages on form fields, keyboard
  navigation for tabs, a skip link, status never shown by colour alone, and
  reduced-motion respected.
- **Live without a refresh:** a run page polls only while the run is active and
  stops when it finishes.
- **A correct diff after merge:** changes are compared with the file at the
  commit the run started from, not with today's branch.

The login token is kept in `localStorage`, which is simple and survives a
reload, but is readable by any script on the page. That is acceptable for a
demo that ships no third-party scripts; a production system would use an
HttpOnly cookie.

## Limits

- A small local model is slow and frequently wrong; the numbers above say how
  often. The system is designed so a wrong change is caught, not merged.
- Runs execute inside the Java process and are not resumed after a restart.
- Generation rewrites whole files, so only files that fit the model's context
  can be changed.
- The sandbox has internet access while tests run.
- More in [docs/architecture.md](docs/architecture.md#known-limits).

## Repository layout

```
services/project-service/   Java control plane (API, auth, GitHub, runs)
services/ai-service/        Python agent (analysis, plan, generate, sandbox, loop)
services/ai-service/evals/  Evaluation tasks and harness
frontend/                   React web app
demo/registration-service/  Small target repository used for demos and evals
infrastructure/             Ollama Modelfile, E2B template, database init
docs/architecture.md        How it works and why
```
