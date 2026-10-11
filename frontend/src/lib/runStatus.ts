import type { RunStatus } from './types'

export type Tone = 'neutral' | 'busy' | 'ok' | 'warn' | 'bad'

/** The path a successful run takes, in order. */
export const STEPS: { status: RunStatus; label: string; detail: string }[] = [
  { status: 'QUEUED', label: 'Queued', detail: 'Waiting for a worker' },
  { status: 'ANALYZING', label: 'Analyse', detail: 'Reading the repository' },
  { status: 'PLANNING', label: 'Plan', detail: 'Deciding what to change' },
  {
    status: 'GENERATING_AND_TESTING',
    label: 'Write and test',
    detail: 'Generating code and running tests in a sandbox',
  },
  { status: 'OPENING_PR', label: 'Propose', detail: 'Opening the pull request' },
]

interface StatusInfo {
  label: string
  tone: Tone
  /** One sentence shown at the top of a finished run. */
  summary?: string
}

export const STATUS: Record<RunStatus, StatusInfo> = {
  QUEUED: { label: 'Queued', tone: 'neutral' },
  ANALYZING: { label: 'Analysing', tone: 'busy' },
  PLANNING: { label: 'Planning', tone: 'busy' },
  GENERATING_AND_TESTING: { label: 'Writing and testing', tone: 'busy' },
  OPENING_PR: { label: 'Opening pull request', tone: 'busy' },
  COMPLETED: {
    label: 'Pull request open',
    tone: 'ok',
    summary: 'The tests passed and a pull request is waiting for your review.',
  },
  TESTS_FAILED: {
    label: 'Tests failed',
    tone: 'bad',
    summary:
      'The tests were still failing after the last attempt, so no pull request was opened.',
  },
  SANDBOX_UNAVAILABLE: {
    label: 'Not tested',
    tone: 'warn',
    summary:
      'Changes were generated but could not be tested, so no pull request was opened.',
  },
  FAILED: { label: 'Failed', tone: 'bad', summary: 'The run stopped before it could finish.' },
}

/**
 * How far along the happy path a run got. A run that ended badly keeps the
 * position of the last step it reached, so the tracker shows where it stopped.
 */
export function stepIndex(status: RunStatus, hasPlan: boolean, hasAnalysis: boolean): number {
  const direct = STEPS.findIndex((step) => step.status === status)
  if (direct >= 0) return direct
  if (status === 'COMPLETED') return STEPS.length
  if (status === 'TESTS_FAILED' || status === 'SANDBOX_UNAVAILABLE') return 3
  // FAILED: infer from what the run managed to produce.
  if (hasPlan) return 3
  if (hasAnalysis) return 2
  return 1
}
