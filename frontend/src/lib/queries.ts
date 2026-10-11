import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { Project, Run } from './types'

// One place that names every piece of server state, so a mutation knows
// exactly which cached data it has made stale.
const keys = {
  connection: ['connection'] as const,
  repositories: ['repositories'] as const,
  projects: ['projects'] as const,
  runs: (projectId: number) => ['projects', projectId, 'runs'] as const,
  run: (runId: string) => ['runs', runId] as const,
  file: (projectId: number, path: string, ref: string | null) =>
    ['projects', projectId, 'file', ref, path] as const,
}

const RUN_POLL_MS = 3000

export function useConnection() {
  return useQuery({ queryKey: keys.connection, queryFn: api.connection })
}

export function useRepositories(enabled: boolean) {
  return useQuery({
    queryKey: keys.repositories,
    queryFn: api.repositories,
    enabled,
    staleTime: 60_000,
  })
}

export function useProjects() {
  return useQuery({ queryKey: keys.projects, queryFn: api.projects })
}

export function useProject(projectId: number) {
  const projects = useProjects()
  return {
    ...projects,
    data: projects.data?.find((project) => project.id === projectId),
    notFound: projects.isSuccess && !projects.data.some((project) => project.id === projectId),
  }
}

export function useRuns(projectId: number) {
  return useQuery({
    queryKey: keys.runs(projectId),
    queryFn: () => api.runs(projectId),
    // Keep the list fresh while anything in it is still moving.
    refetchInterval: (query) =>
      query.state.data?.some((run) => run.active) ? RUN_POLL_MS : false,
  })
}

export function useRun(runId: string) {
  return useQuery({
    queryKey: keys.run(runId),
    queryFn: () => api.run(runId),
    // Poll only while the run is in progress; a finished run never changes.
    refetchInterval: (query) => (query.state.data?.active ? RUN_POLL_MS : false),
  })
}

/** A file at the run's base commit. Content at a commit never changes, so cache it forever. */
export function useOriginalFile(
  projectId: number,
  path: string,
  ref: string | null,
  enabled: boolean,
) {
  return useQuery({
    queryKey: keys.file(projectId, path, ref),
    queryFn: () => api.file(projectId, path, ref),
    enabled,
    staleTime: Infinity,
    retry: false,
  })
}

export function useCreateProject() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ name, repository }: { name: string; repository: string }) => {
      const [owner, repositoryName] = repository.split('/')
      const project = await api.createProject(name, repository)
      try {
        return await api.connectRepository(project.id, owner, repositoryName)
      } catch (error) {
        // A project without a repository is useless here, so do not leave one
        // behind when the second step fails.
        await api.deleteProject(project.id).catch(() => undefined)
        throw error
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.projects }),
  })
}

export function useDeleteProject() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (projectId: number) => api.deleteProject(projectId),
    onSuccess: (_result, projectId) => {
      queryClient.setQueryData<Project[]>(keys.projects, (projects) =>
        projects?.filter((project) => project.id !== projectId),
      )
    },
  })
}

export function useStartRun(projectId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (task: string) => api.startRun(projectId, task),
    onSuccess: (run: Run) => {
      // Seed the cache so the run page renders at once instead of loading.
      queryClient.setQueryData(keys.run(run.id), run)
      return queryClient.invalidateQueries({ queryKey: keys.runs(projectId) })
    },
  })
}
