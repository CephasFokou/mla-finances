import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ledgerApi } from './api'
import type { ActivityInput, MemberInput, PaymentChannelInput, TransferInput } from './types'

// Les rapports bougent à chaque mouvement : staleTime court, et toute mutation du grand livre
// invalide la clé « reports » (voir invalidateLedger).
const REPORT_STALE_TIME = 15_000

export function useInvalidateLedger() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['reports'] }),
      queryClient.invalidateQueries({ queryKey: ['contributions'] }),
      queryClient.invalidateQueries({ queryKey: ['withdrawals'] }),
      queryClient.invalidateQueries({ queryKey: ['project-balance'] }),
      queryClient.invalidateQueries({ queryKey: ['category-balance'] }),
    ])
}

export function useLedgerSummaryQuery(year: number) {
  return useQuery({
    queryKey: ['reports', 'summary', year],
    queryFn: () => ledgerApi.summary(year),
    staleTime: REPORT_STALE_TIME,
  })
}

export function useMonthlyCalendarQuery(year: number) {
  return useQuery({
    queryKey: ['reports', 'calendar', year],
    queryFn: () => ledgerApi.calendar(year),
    staleTime: REPORT_STALE_TIME,
  })
}

export function useCollectionsReportQuery() {
  return useQuery({
    queryKey: ['reports', 'collections'],
    queryFn: ledgerApi.collections,
    staleTime: REPORT_STALE_TIME,
  })
}

export function useActivityReportQuery() {
  return useQuery({
    queryKey: ['reports', 'activities'],
    queryFn: ledgerApi.activityReport,
    staleTime: REPORT_STALE_TIME,
  })
}

export function useActivitiesQuery() {
  return useQuery({ queryKey: ['reference', 'activities'], queryFn: ledgerApi.activities, staleTime: 60_000 })
}

export function useSaveActivityMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: ActivityInput }) =>
      id ? ledgerApi.updateActivity(id, input) : ledgerApi.createActivity(input),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['reference', 'activities'] }),
        queryClient.invalidateQueries({ queryKey: ['reports'] }),
      ]),
  })
}

export function useMembersQuery() {
  return useQuery({ queryKey: ['members'], queryFn: ledgerApi.members, staleTime: 60_000 })
}

export function useSaveMemberMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: MemberInput }) =>
      id ? ledgerApi.updateMember(id, input) : ledgerApi.createMember(input),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['members'] }),
        queryClient.invalidateQueries({ queryKey: ['reference', 'donors'] }),
        queryClient.invalidateQueries({ queryKey: ['reports'] }),
      ]),
  })
}

export function useCreateTransferMutation() {
  const invalidate = useInvalidateLedger()
  return useMutation({
    mutationFn: (input: TransferInput) => ledgerApi.createTransfer(input),
    onSuccess: () => invalidate(),
  })
}

export function useAllPaymentChannelsQuery() {
  return useQuery({ queryKey: ['payment-channels', 'all'], queryFn: ledgerApi.allPaymentChannels })
}

export function useSavePaymentChannelMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: PaymentChannelInput }) =>
      id ? ledgerApi.updatePaymentChannel(id, input) : ledgerApi.createPaymentChannel(input),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['payment-channels'] }),
        queryClient.invalidateQueries({ queryKey: ['reference', 'payment-channels'] }),
      ]),
  })
}

export function useTogglePaymentChannelMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      ledgerApi.setPaymentChannelActive(id, active),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['payment-channels'] }),
        queryClient.invalidateQueries({ queryKey: ['reference', 'payment-channels'] }),
      ]),
  })
}
