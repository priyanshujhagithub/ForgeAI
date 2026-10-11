// Shapes returned by the ForgeAI Java API (services/project-service).

export interface GitHubConnection {
  connected: boolean
  githubUsername: string | null
}

export interface Repository {
  id: number
  name: string
  full_name: string
  html_url: string
  private: boolean
}

export interface Project {
  id: number
  name: string
  description: string | null
  githubOwner: string | null
  githubRepoName: string | null
  githubRepoUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface Analysis {
  summary: string
  projectType: string
  keyComponents: string[]
  dependencies: string[]
  architectureNotes: string
  relevantFiles: string[]
}

export interface PlanStep {
  order: number
  description: string
  files: string[]
}

export interface Plan {
  taskUnderstanding: string
  filesToModify: string[]
  filesToCreate: string[]
  steps: PlanStep[]
}

export interface CodeChange {
  path: string
  action: 'modify' | 'create' | 'delete'
  content: string
  explanation: string
}

export interface SandboxResult {
  outcome: 'passed' | 'tests_failed' | 'infrastructure_error'
  command: string
  exitCode: number | null
  stdout: string
  stderr: string
  durationSeconds: number
  timedOut: boolean
  message: string | null
}

export interface Diagnosis {
  rootCause: string
  fixStrategy: string
  filesToChange: string[]
}

export interface Attempt {
  number: number
  diagnosis: Diagnosis | null
  changes: CodeChange[]
  sandboxResult: SandboxResult
}

export interface LoopResult {
  outcome: 'passed' | 'tests_failed' | 'sandbox_unavailable' | 'infrastructure_error'
  finalChanges: CodeChange[]
  attempts: Attempt[]
  sandboxSeconds: number
  estimatedCostUsd: number
  message: string | null
}

export type RunStatus =
  | 'QUEUED'
  | 'ANALYZING'
  | 'PLANNING'
  | 'GENERATING_AND_TESTING'
  | 'OPENING_PR'
  | 'COMPLETED'
  | 'TESTS_FAILED'
  | 'SANDBOX_UNAVAILABLE'
  | 'FAILED'

export interface Run {
  id: string
  projectId: number
  task: string
  status: RunStatus
  active: boolean
  analysis: Analysis | null
  plan: Plan | null
  result: LoopResult | null
  baseBranch: string | null
  baseSha: string | null
  branchName: string | null
  pullRequestNumber: number | null
  pullRequestUrl: string | null
  errorMessage: string | null
  sandboxSeconds: number | null
  estimatedCostUsd: number | null
  createdAt: string
  updatedAt: string
  finishedAt: string | null
}

export interface RepositoryFile {
  path: string
  content: string
  sha: string
}
