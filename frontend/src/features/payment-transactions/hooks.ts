import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { paymentTransactionsApi } from './api'
import type { PaymentTransactionCancelInput, PaymentTransactionFilters } from './types'

const KEYS = {
  list: (filters: PaymentTransactionFilters) => ['payment-transactions', filters] as const,
}

export function usePaymentTransactionsQuery(filters: PaymentTransactionFilters) {
  return useQuery({
    queryKey: KEYS.list(filters),
    queryFn: () => paymentTransactionsApi.list(filters),
    placeholderData: (previous) => previous,
  })
}

function invalidateDerivedQueries(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ['payment-transactions'] })
  void queryClient.invalidateQueries({ queryKey: ['contributions'] })
  void queryClient.invalidateQueries({ queryKey: ['project-balance'] })
  void queryClient.invalidateQueries({ queryKey: ['category-balance'] })
}

export function useConfirmPaymentTransactionMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => paymentTransactionsApi.confirm(id),
    onSuccess: () => invalidateDerivedQueries(queryClient),
  })
}

export function useCancelPaymentTransactionMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: PaymentTransactionCancelInput }) =>
      paymentTransactionsApi.cancel(id, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['payment-transactions'] })
    },
  })
}
