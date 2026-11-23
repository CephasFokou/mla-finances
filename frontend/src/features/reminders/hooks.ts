import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { remindersApi } from './api'
import type { ReminderSettingsInput } from './types'

/** Motifs de l'écran Relances : contribution mensuelle du mois en cours, collectes relançables en cours. */
export function useReminderReasonsQuery() {
  return useQuery({ queryKey: ['reminders', 'reasons'], queryFn: remindersApi.reasons, staleTime: 60_000 })
}

export function useReminderOverviewQuery(month: string) {
  return useQuery({
    queryKey: ['reminders', month],
    queryFn: () => remindersApi.overview(month),
    placeholderData: (previous) => previous,
  })
}

export function useReminderPreviewQuery(donorId: number | null, month: string) {
  return useQuery({
    queryKey: ['reminder-preview', donorId, month],
    queryFn: () => remindersApi.preview(donorId as number, month),
    enabled: donorId !== null,
  })
}

export function useReminderCaseQuery(caseId: number | null) {
  return useQuery({
    queryKey: ['reminder-case', caseId],
    queryFn: () => remindersApi.detail(caseId as number),
    enabled: caseId !== null,
  })
}

function useInvalidate() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['reminders'] })
    void queryClient.invalidateQueries({ queryKey: ['reminder-case'] })
    void queryClient.invalidateQueries({ queryKey: ['reminder-preview'] })
  }
}

export function useSendReminderMutation() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ donorId, month }: { donorId: number; month: string }) => remindersApi.send(donorId, month),
    onSettled: invalidate,
  })
}

export function useBulkReminderMutation() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ donorIds, month }: { donorIds: number[]; month: string }) =>
      remindersApi.bulk(donorIds, month),
    onSettled: invalidate,
  })
}

export function useCloseReminderMutation() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ donorId, month, reason }: { donorId: number; month: string; reason: string }) =>
      remindersApi.closeForMember(donorId, month, reason),
    onSettled: invalidate,
  })
}

export function useAcknowledgeReminderMutation() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (caseId: number) => remindersApi.acknowledge(caseId),
    onSettled: invalidate,
  })
}

/** Badge de la navigation : dossiers avec une réponse à lire ou un paiement déclaré non retrouvé. */
export function useReminderAttentionQuery(enabled: boolean) {
  return useQuery({
    queryKey: ['reminders', 'attention'],
    queryFn: remindersApi.attention,
    enabled,
    refetchInterval: 60_000,
  })
}

export function useReminderSettingsQuery() {
  return useQuery({ queryKey: ['reminder-settings'], queryFn: remindersApi.settings })
}

export function useUpdateReminderSettingsMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ReminderSettingsInput) => remindersApi.updateSettings(input),
    onSuccess: (data) => {
      queryClient.setQueryData(['reminder-settings'], data)
      void queryClient.invalidateQueries({ queryKey: ['reminders'] })
    },
  })
}
