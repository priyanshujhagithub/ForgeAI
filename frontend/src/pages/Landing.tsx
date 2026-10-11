import { Link } from 'react-router'
import { ButtonLink } from '../components/Button'
import { Logo } from '../components/Logo'
import { useAuth } from '../lib/auth'
import styles from './Landing.module.css'

const STEPS = [
  {
    name: 'Read',
    text: 'ForgeAI pins your default branch to one commit and reads the repository at that point: the file tree, the build manifest, the source, the tests.',
  },
  {
    name: 'Plan',
    text: 'It restates the task in terms of your code and lists the files it will change. Every path is checked against the real tree before anything is written.',
  },
  {
    name: 'Write',
    text: 'It writes each file in full, one at a time, so later files agree with earlier ones. The plan fixes where code goes; the model only fills in the content.',
  },
  {
    name: 'Test',
    text: 'Your own test suite runs against the change inside a disposable sandbox. If it fails, the agent states the root cause, fixes those files, and runs again, up to three times.',
  },
  {
    name: 'Propose',
    text: 'Passing changes become one commit on a new branch and a pull request that carries the plan and the test history. Merging stays with you.',
  },
]

const SAFEGUARDS = [
  {
    title: 'Output is checked, not believed',
    text: 'Every model reply must match a schema and the real repository. Invented paths, half-written files and "rest of the code here" placeholders are sent back.',
  },
  {
    title: 'Nothing runs on your machine',
    text: 'Generated code only ever executes in an isolated sandbox that is destroyed when the tests finish.',
  },
  {
    title: 'Your tests are the judge',
    text: 'A change is proposed only when the repository’s own tests pass. If they still fail after three attempts, the run stops and says so.',
  },
  {
    title: 'A person decides',
    text: 'ForgeAI never pushes to your default branch and never merges. The result is always a pull request waiting for review.',
  },
]

const RESULTS = [
  {
    figure: '3 of 8',
    label: 'benchmark tasks solved',
    note: 'by an 8-billion-parameter model on a laptop GPU. A task counts only if a hidden acceptance test passes too.',
  },
  {
    figure: '2.5 min',
    label: 'for a single-file task',
    note: 'from task to tested change on that same laptop. Multi-file tasks with retries take longer.',
  },
  {
    figure: '$0.001',
    label: 'of sandbox time per run',
    note: 'estimated from measured sandbox seconds. Each attempt gets its own short-lived sandbox.',
  },
]

export function Landing() {
  const { session } = useAuth()
  const start = session ? '/app' : '/register'

  return (
    <div className={styles.page}>
      <header className={`${styles.header} vt-header`}>
        <div className={`container ${styles.bar}`}>
          <Link to="/" className={styles.brand} aria-label="ForgeAI home">
            <Logo />
          </Link>
          <nav className={styles.nav} aria-label="Page sections">
            <a href="#how">How it works</a>
            <a href="#safeguards">Safeguards</a>
            <a href="#results">Results</a>
          </nav>
          <div className={styles.actions}>
            {session ? (
              <ButtonLink to="/app" variant="primary">
                Open app
              </ButtonLink>
            ) : (
              <>
                <ButtonLink to="/login" variant="quiet">
                  Sign in
                </ButtonLink>
                <ButtonLink to="/register" variant="primary">
                  Get started
                </ButtonLink>
              </>
            )}
          </div>
        </div>
      </header>

      <main>
        <section className={`container ${styles.hero}`}>
          <div className={styles.pitch}>
            <p className="eyebrow">An AI software-engineering agent</p>
            <h1 className={styles.headline}>
              Hand it a task. Get back a <em>tested</em> pull request.
            </h1>
            <p className={styles.lede}>
              ForgeAI reads your repository, plans the change, writes the code, runs your tests in
              an isolated sandbox and fixes what fails. You review the pull request and decide.
            </p>
            <div className={styles.cta}>
              <ButtonLink to={start} variant="primary" size="lg">
                Start a run
              </ButtonLink>
              <a className={styles.textLink} href="#how">
                See how it works
              </a>
            </div>
            <p className={styles.footnote}>
              Works with a local model through Ollama, or any OpenAI-compatible API.
            </p>
          </div>

          <ExampleRun />
        </section>

        <section id="how" className={styles.band}>
          <div className={`container ${styles.split}`}>
            <div className={styles.sectionHead}>
              <p className="eyebrow">How it works</p>
              <h2 className={styles.title}>Five steps, each one checked before the next.</h2>
            </div>
            <ol className={styles.steps}>
              {STEPS.map((step, index) => (
                <li key={step.name} className={styles.step}>
                  <span className={styles.stepNumber} aria-hidden="true">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <div>
                    <h3 className={styles.stepName}>{step.name}</h3>
                    <p className={styles.stepText}>{step.text}</p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section id="safeguards" className={`container ${styles.section}`}>
          <div className={styles.sectionHead}>
            <p className="eyebrow">Safeguards</p>
            <h2 className={styles.title}>Built on the assumption that the model is wrong.</h2>
            <p className={styles.sectionLede}>
              A language model will confidently hand you broken code. The system around it is
              designed so that a wrong answer is caught, not merged.
            </p>
          </div>
          <ul className={styles.safeguards}>
            {SAFEGUARDS.map((item) => (
              <li key={item.title} className={styles.safeguard}>
                <h3 className={styles.safeguardTitle}>{item.title}</h3>
                <p className={styles.safeguardText}>{item.text}</p>
              </li>
            ))}
          </ul>
        </section>

        <section id="results" className={styles.band}>
          <div className="container">
            <div className={styles.sectionHead}>
              <p className="eyebrow">Results</p>
              <h2 className={styles.title}>Measured, not promised.</h2>
              <p className={styles.sectionLede}>
                These numbers come from running the agent on a fixed set of tasks with a small
                local model. They are modest on purpose: this is what it does, not what it might.
              </p>
            </div>
            <dl className={styles.results}>
              {RESULTS.map((result) => (
                <div key={result.label} className={styles.result}>
                  <dt className={styles.resultLabel}>{result.label}</dt>
                  <dd className={styles.resultFigure}>{result.figure}</dd>
                  <dd className={styles.resultNote}>{result.note}</dd>
                </div>
              ))}
            </dl>
            <p className={styles.resultsFoot}>
              The model is the limit, and it is swappable. When the agent’s tests fail, nothing is
              proposed, so a miss costs a few minutes, not a broken branch.
            </p>
          </div>
        </section>

        <section className={`container ${styles.closing}`}>
          <h2 className={styles.closingTitle}>Point it at a repository and give it a task.</h2>
          <ButtonLink to={start} variant="primary" size="lg">
            {session ? 'Open your projects' : 'Create an account'}
          </ButtonLink>
        </section>
      </main>

      <footer className={styles.footer}>
        <div className={`container ${styles.footerBar}`}>
          <Logo />
          <p>Spring Boot, FastAPI and React. Generated code runs only in an E2B sandbox.</p>
        </div>
      </footer>
    </div>
  )
}

/** A real run from this project’s demo repository, shown as the product would show it. */
function ExampleRun() {
  const steps = [
    'Read the repository at one commit',
    'Planned the change',
    'Wrote the code',
    'Tests passed in the sandbox',
    'Opened pull request #1',
  ]

  return (
    <figure className={styles.example}>
      <div className={styles.exampleHead}>
        <span className="mono">demo / registration-service</span>
        <span className={styles.exampleStatus}>Pull request open</span>
      </div>
      <p className={styles.exampleTask}>
        Add GET /health that returns HTTP 200 with the JSON body {'{"status": "ok"}'}.
      </p>
      <ol className={styles.exampleSteps}>
        {steps.map((step) => (
          <li key={step}>
            <span className={styles.check} aria-hidden="true">
              ✓
            </span>
            {step}
          </li>
        ))}
      </ol>
      <dl className={styles.exampleMeta}>
        <div>
          <dt>Total</dt>
          <dd>4 min 05 s</dd>
        </div>
        <div>
          <dt>Sandbox</dt>
          <dd>15.6 s</dd>
        </div>
        <div>
          <dt>Attempts</dt>
          <dd>1</dd>
        </div>
      </dl>
      <figcaption className={styles.exampleCaption}>
        A real run on this project’s demo repository, using a local model.
      </figcaption>
    </figure>
  )
}
