import { Link } from 'react-router'
import { ButtonLink } from '../components/Button'
import { ExampleRun } from '../components/ExampleRun'
import { Logo } from '../components/Logo'
import { ThemeToggle } from '../components/ThemeToggle'
import { useAuth } from '../lib/auth'
import { nth, useActiveSection, useRevealOnScroll } from '../lib/motion'
import styles from './Landing.module.css'

const SECTIONS = [
  { id: 'how', label: 'How it works' },
  { id: 'safeguards', label: 'Safeguards' },
  { id: 'results', label: 'Results' },
]
const SECTION_IDS = SECTIONS.map((section) => section.id)

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
    text: 'If the tests still fail after three attempts, nothing is proposed. A repository with no tests at all still gets a pull request, clearly marked untested.',
  },
  {
    title: 'A person decides',
    text: 'ForgeAI never pushes to your default branch and never merges. The result is always a pull request waiting for review.',
  },
]

const RESULTS = [
  {
    figure: '8 of 8',
    label: 'benchmark tasks solved',
    note: 'by Gemini 3.5 Flash on its free tier. A task counts only if a hidden acceptance test, which the agent never sees, passes too.',
  },
  {
    figure: '66 s',
    label: 'per task, on average',
    note: 'from task to tested change, sandbox included, across the eight benchmark tasks.',
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
  const page = useRevealOnScroll<HTMLDivElement>()
  const activeSection = useActiveSection(SECTION_IDS)

  return (
    <div className={styles.page} ref={page}>
      <header className={`${styles.header} vt-header`}>
        <div className={`container ${styles.bar}`}>
          <Link to="/" className={styles.brand} aria-label="ForgeAI home">
            <Logo />
          </Link>
          <nav className={styles.nav} aria-label="Page sections">
            {SECTIONS.map((section) => (
              <a
                key={section.id}
                href={`#${section.id}`}
                aria-current={activeSection === section.id ? 'true' : undefined}
              >
                {section.label}
              </a>
            ))}
          </nav>
          <div className={styles.actions}>
            <ThemeToggle />
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
        {/* The hero is on screen when the page opens, so it arrives on load, not on scroll. */}
        <div className={styles.heroBand}>
          <section className={`container ${styles.hero}`}>
            <div className={styles.pitch}>
              <p className="eyebrow enter" style={nth(0)}>
                An AI software-engineering agent
              </p>
              <h1 className={`${styles.headline} enter`} style={nth(1)}>
                Hand it a task. Get back a <em>tested</em> pull request.
              </h1>
              <p className={`${styles.lede} enter`} style={nth(2)}>
                ForgeAI reads your repository, plans the change, writes the code, runs your tests
                in an isolated sandbox and fixes what fails. You review the pull request and
                decide.
              </p>
              <div className={`${styles.cta} enter`} style={nth(3)}>
                <ButtonLink to={start} variant="primary" size="lg" arrow>
                  Start a run
                </ButtonLink>
                <a className={styles.textLink} href="#how">
                  See how it works
                </a>
              </div>
              <p className={`${styles.footnote} enter`} style={nth(4)}>
                Runs on Google Gemini's free tier. Nothing to install to try it.
              </p>
            </div>

            <div className={`${styles.exampleSlot} enter`} style={nth(3)}>
              <ExampleRun autoplay />
            </div>
          </section>
        </div>

        <section id="how" className={styles.band}>
          <div className={`container ${styles.split}`}>
            <div className={styles.sectionHead} data-reveal>
              <p className="eyebrow">How it works</p>
              <h2 className={styles.title}>Five steps, each one checked before the next.</h2>
            </div>
            <ol className={styles.steps}>
              {STEPS.map((step, index) => (
                <li key={step.name} className={styles.step} data-reveal>
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
          <div className={styles.sectionHead} data-reveal>
            <p className="eyebrow">Safeguards</p>
            <h2 className={styles.title}>Built on the assumption that the model is wrong.</h2>
            <p className={styles.sectionLede}>
              A language model will confidently hand you broken code. The system around it is
              designed so that a wrong answer is caught, not merged.
            </p>
          </div>
          <ul className={styles.safeguards}>
            {SAFEGUARDS.map((item, index) => (
              // Two columns on a wide screen, so the stagger runs across each row.
              <li key={item.title} className={styles.safeguard} data-reveal style={nth(index % 2)}>
                <h3 className={styles.safeguardTitle}>{item.title}</h3>
                <p className={styles.safeguardText}>{item.text}</p>
              </li>
            ))}
          </ul>
        </section>

        <section id="results" className={styles.band}>
          <div className="container">
            <div className={styles.sectionHead} data-reveal>
              <p className="eyebrow">Results</p>
              <h2 className={styles.title}>Measured, not promised.</h2>
              <p className={styles.sectionLede}>
                These numbers come from running the agent on a fixed set of eight small tasks
                against a demo repository. They are what it did, not what it might do.
              </p>
            </div>
            <dl className={styles.results}>
              {RESULTS.map((result, index) => (
                <div
                  key={result.label}
                  className={styles.result}
                  data-reveal
                  style={nth(index * 2)}
                >
                  <dt className={styles.resultLabel}>{result.label}</dt>
                  <dd className={styles.resultFigure}>{result.figure}</dd>
                  <dd className={styles.resultNote}>{result.note}</dd>
                </div>
              ))}
            </dl>
            <p className={styles.resultsFoot} data-reveal>
              The model can still be wrong, which is why the tests decide. When the agent’s tests
              fail, nothing is proposed, so a miss costs a few minutes, not a broken branch.
            </p>
          </div>
        </section>

        <section className={`container ${styles.closing}`} data-reveal>
          <h2 className={styles.closingTitle}>Point it at a repository and give it a task.</h2>
          <ButtonLink to={start} variant="primary" size="lg" arrow>
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
