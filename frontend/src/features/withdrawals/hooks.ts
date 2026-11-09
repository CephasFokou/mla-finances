import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { withdrawalsApi } from './api'
import type { WithdrawalCreateInput, WithdrawalFilters, WithdrawalRejectInput } from './types'

const KEYS = {
  list: (filters: WithdrawalFilters) => ['withdrawals', filters] as const,
  detail: (id: number) => ['withdrawal', id] as const,
  projectBalance: (projectId: number) => ['project-balance', projectId] as const,
  categoryBalance: (categoryId: number) => ['category-balance', categoryId] as const,
}

export function useWithdrawalsQuery(filters: WithdrawalFilters) {
  return useQuery({
    queryKey: KEYS.list(filters),
    queryFn: () => withdrawalsApi.list(filters),
    placeholderData: (previous) => previous,
  })
}

export function useWithdrawalQuery(id: number) {
  return useQuery({
    queryKey: KEYS.detail(id),
    queryFn: () => withdrawalsApi.get(id),
    enabled: Number.isFinite(id),
  })
}

export function useWithdrawalProjectBalanceQuery(projectId: number | undefined) {
  return useQuery({
    queryKey: KEYS.projectBalance(projectId ?? -1),
    queryFn: () => withdrawalsApi.projectBalance(projectId as number),
    enabled: projectId !== undefined,
  })
}

export function useWithdrawalCategoryBalanceQuery(categoryId: number | undefined) {
  return useQuery({
    queryKey: KEYS.categoryBalance(categoryId ?? -1),
    queryFn: () => withdrawalsApi.categoryBalance(categoryId as number),
    enabled: categoryId !== undefined,
  })
}

/** Invalide la liste, les soldes dérivés (réel + prévisionnel) et, pour les mutations qui la
 * connaissent, la fiche détail d'un retrait après une écriture. */
function invalidateDerivedQueries(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ['withdrawals'] })
  void queryClient.invalidateQueries({ queryKey: ['project-balance'] })
  void queryClient.invalidateQueries({ queryKey: ['category-balance'] })
  void queryClient.invalidateQueries({ queryKey: ['reports'] })
}

export function useCreateWithdrawalMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: WithdrawalCreateInput) =>
      input.operationDate ? withdrawalsApi.record(input) : withdrawalsApi.create(input),
    onSuccess: () => invalidateDerivedQueries(queryClient),
  })
}

function useWithdrawalDecisionMutation<TArgs = void>(
  id: number,
  action: (id: number, args: TArgs) => ReturnType<typeof withdrawalsApi.approve>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (args: TArgs) => action(id, args),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEYS.detail(id) })
      invalidateDerivedQueries(queryClient)
    },
  })
}

export function useApproveWithdrawalMutation(id: number) {
  return useWithdrawalDecisionMutation<void>(id, () => withdrawalsApi.approve(id))
}

export function useRejectWithdrawalMutation(id: number) {
  return useWithdrawalDecisionMutation<WithdrawalRejectInput>(id, (_id, input) =>
    withdrawalsApi.reject(id, input),
  )
}

export function useCancelWithdrawalMutation(id: number) {
  return useWithdrawalDecisionMutation<void>(id, () => withdrawalsApi.cancel(id))
}

export function useProcessWithdrawalMutation(id: number) {
  return useWithdrawalDecisionMutation<void>(id, () => withdrawalsApi.process(id))
}
