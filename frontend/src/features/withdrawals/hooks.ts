import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { withdrawalsApi } from './api'
import type { WithdrawalCreateInput, WithdrawalFilters, WithdrawalRejectInput } from './types'

const KEYS = {
  list: (filters: WithdrawalFilters) => ['withdrawals', filters] as const,
  detail: (id: number) => ['withdrawal', id] as const,
  // Sous ['withdrawals'] : rafraîchie avec la liste après chaque sortie (le reste d'une clôturée baisse).
  projects: ['withdrawals', 'projects'] as const,
  projectBalance: (projectId: number) => ['project-balance', projectId] as const,
  categoryBalance: (categoryId: number) => ['category-balance', categoryId] as const,
  payoutOptions: ['withdrawal-payout-options'] as const,
  mobileMoneyBalance: ['mobile-money-balance'] as const,
}

const ACTIVE_PAYOUT_REFRESH_MS = 10_000

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
    // Payout en cours : la fiche se rafraîchit seule (le webhook ou le polling backend tranche).
    refetchInterval: (query) => {
      const payoutStatus = query.state.data?.payoutStatus
      return payoutStatus === 'PROCESSING' || payoutStatus === 'PENDING_APPROVAL'
        ? ACTIVE_PAYOUT_REFRESH_MS
        : false
    },
  })
}

export function useWithdrawableProjectsQuery() {
  return useQuery({ queryKey: KEYS.projects, queryFn: withdrawalsApi.projects })
}

export function usePayoutOptionsQuery() {
  return useQuery({
    queryKey: KEYS.payoutOptions,
    queryFn: withdrawalsApi.payoutOptions,
    staleTime: 5 * 60_000,
  })
}

export function useMobileMoneyBalanceQuery() {
  return useQuery({
    queryKey: KEYS.mobileMoneyBalance,
    queryFn: withdrawalsApi.mobileMoneyBalance,
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
  void queryClient.invalidateQueries({ queryKey: KEYS.mobileMoneyBalance })
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

export function usePayoutWithdrawalMutation(id: number) {
  return useWithdrawalDecisionMutation<string>(id, (_id, recipientKey) =>
    withdrawalsApi.payout(id, recipientKey),
  )
}

export function useRecheckPayoutMutation(id: number) {
  return useWithdrawalDecisionMutation<void>(id, () => withdrawalsApi.recheckPayout(id))
}
