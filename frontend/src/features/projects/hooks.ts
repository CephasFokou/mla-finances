import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { categoriesApi, projectsApi } from './api'
import type {
  CategoryInput,
  ProjectCreateInput,
  ProjectOccurrenceInput,
  ProjectStatus,
  ProjectUpdateInput,
} from './types'

const KEYS = {
  list: (status?: ProjectStatus) => ['projects', status ?? 'all'] as const,
  detail: (id: number) => ['project', id] as const,
  occurrences: (seriesId: string) => ['project-occurrences', seriesId] as const,
  categories: ['categories', 'all'] as const,
}

/** Invalide les vues dérivées d'un projet après une écriture : liste admin, dropdown de
 * référence (features/reference-data) et détail le cas échéant. */
function invalidateProjectQueries(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ['projects'] })
  void queryClient.invalidateQueries({ queryKey: ['reference', 'projects'] })
}

export function useProjectsAdminQuery(status?: ProjectStatus) {
  return useQuery({
    queryKey: KEYS.list(status),
    queryFn: () => projectsApi.list(status),
  })
}

export function useProjectQuery(id: number) {
  return useQuery({
    queryKey: KEYS.detail(id),
    queryFn: () => projectsApi.get(id),
    enabled: Number.isFinite(id),
  })
}

export function useProjectOccurrencesQuery(seriesId: string | null) {
  return useQuery({
    queryKey: KEYS.occurrences(seriesId ?? ''),
    queryFn: () => projectsApi.occurrencesOfSeries(seriesId as string),
    enabled: !!seriesId,
  })
}

export function useCreateProjectMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ProjectCreateInput) => projectsApi.create(input),
    onSuccess: () => invalidateProjectQueries(queryClient),
  })
}

export function useUpdateProjectMutation(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ProjectUpdateInput) => projectsApi.update(id, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEYS.detail(id) })
      invalidateProjectQueries(queryClient)
    },
  })
}

function useProjectLifecycleMutation(
  id: number,
  action: (id: number) => ReturnType<typeof projectsApi.activate>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => action(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEYS.detail(id) })
      invalidateProjectQueries(queryClient)
    },
  })
}

export function useActivateProjectMutation(id: number) {
  return useProjectLifecycleMutation(id, projectsApi.activate)
}

export function useCompleteProjectMutation(id: number) {
  return useProjectLifecycleMutation(id, projectsApi.complete)
}

export function useArchiveProjectMutation(id: number) {
  return useProjectLifecycleMutation(id, projectsApi.archive)
}

export function useCreateOccurrenceMutation(sourceId: number, seriesId: string | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ProjectOccurrenceInput) => projectsApi.duplicateOccurrence(sourceId, input),
    onSuccess: () => {
      if (seriesId) {
        void queryClient.invalidateQueries({ queryKey: KEYS.occurrences(seriesId) })
      }
      invalidateProjectQueries(queryClient)
    },
  })
}

export function useCategoriesAdminQuery() {
  return useQuery({
    queryKey: KEYS.categories,
    queryFn: categoriesApi.listAll,
  })
}

function invalidateCategoryQueries(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ['categories'] })
  void queryClient.invalidateQueries({ queryKey: ['reference', 'categories'] })
}

export function useCreateCategoryMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CategoryInput) => categoriesApi.create(input),
    onSuccess: () => invalidateCategoryQueries(queryClient),
  })
}

export function useUpdateCategoryMutation(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CategoryInput) => categoriesApi.update(id, input),
    onSuccess: () => invalidateCategoryQueries(queryClient),
  })
}

export function useToggleCategoryActiveMutation(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (active: boolean) => (active ? categoriesApi.deactivate(id) : categoriesApi.activate(id)),
    onSuccess: () => invalidateCategoryQueries(queryClient),
  })
}
