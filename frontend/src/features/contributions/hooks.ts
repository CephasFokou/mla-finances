import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { contributionsApi } from './api'
import type {
  ContributionCancelInput,
  ContributionCreateInput,
  ContributionFilters,
  ContributionUpdateInput,
} from './types'

const KEYS = {
  list: (filters: ContributionFilters) => ['contributions', filters] as const,
  detail: (id: number) => ['contribution', id] as const,
  projectBalance: (projectId: number) => ['project-balance', projectId] as const,
  categoryBalance: (categoryId: number) => ['category-balance', categoryId] as const,
}

export function useContributionsQuery(filters: ContributionFilters) {
  return useQuery({
    queryKey: KEYS.list(filters),
    queryFn: () => contributionsApi.list(filters),
    placeholderData: (previous) => previous,
  })
}

export function useContributionQuery(id: number) {
  return useQuery({
    queryKey: KEYS.detail(id),
    queryFn: () => contributionsApi.get(id),
    enabled: Number.isFinite(id),
  })
}

export function useProjectBalanceQuery(projectId: number | undefined) {
  return useQuery({
    queryKey: KEYS.projectBalance(projectId ?? -1),
    queryFn: () => contributionsApi.projectBalance(projectId as number),
    enabled: projectId !== undefined,
  })
}

export function useCategoryBalanceQuery(categoryId: number | undefined) {
  return useQuery({
    queryKey: KEYS.categoryBalance(categoryId ?? -1),
    queryFn: () => contributionsApi.categoryBalance(categoryId as number),
    enabled: categoryId !== undefined,
  })
}

/**
 * Invalide toutes les vues dérivées d'une contribution (liste + soldes) après une écriture.
 * Le détail de la contribution elle-même est invalidé séparément par l'appelant, qui connaît l'id.
 */
function invalidateDerivedQueries(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ['contributions'] })
  void queryClient.invalidateQueries({ queryKey: ['project-balance'] })
  void queryClient.invalidateQueries({ queryKey: ['category-balance'] })
  void queryClient.invalidateQueries({ queryKey: ['reports'] })
}

export function useCreateContributionMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ContributionCreateInput) => contributionsApi.create(input),
    onSuccess: () => invalidateDerivedQueries(queryClient),
  })
}

export function useUpdateContributionMutation(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ContributionUpdateInput) => contributionsApi.update(id, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['contribution', id] })
      invalidateDerivedQueries(queryClient)
    },
  })
}

export function useCancelContributionMutation(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ContributionCancelInput) => contributionsApi.cancel(id, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['contribution', id] })
      invalidateDerivedQueries(queryClient)
    },
  })
}
