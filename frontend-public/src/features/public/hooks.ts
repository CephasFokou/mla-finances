import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { publicDonationApi } from './api'
import type { PublicDonationInput, PublicDonationStatus, PublicMonthlyContributionInput } from './types'

const FINAL_STATUSES: PublicDonationStatus[] = ['CONFIRMED', 'FAILED', 'CANCELLED']
/** Fréquence du suivi d'un don en ligne : 3 s (≈ 20 req/min, sous la limite publique de 60/min). */
const STATUS_POLL_INTERVAL_MS = 3000

export function usePublicProjectsQuery() {
  return useQuery({ queryKey: ['public-projects'], queryFn: publicDonationApi.projects })
}

export function usePublicPaymentChannelsQuery() {
  return useQuery({ queryKey: ['public-payment-channels'], queryFn: publicDonationApi.paymentChannels })
}

export function usePublicStatsQuery() {
  return useQuery({ queryKey: ['public-stats'], queryFn: publicDonationApi.stats })
}

export function useSubmitPublicDonationMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: PublicDonationInput | PublicMonthlyContributionInput) =>
      publicDonationApi.donate(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['public-collections'] }),
  })
}

/**
 * Suivi d'un don en ligne pendant que le donateur valide sur son téléphone : interroge le statut
 * toutes les 3 s jusqu'à un statut final, puis s'arrête. Désactivé sans jeton.
 */
export function useDonationStatusQuery(trackingToken: string | null) {
  return useQuery({
    queryKey: ['public-donation-status', trackingToken],
    queryFn: () => publicDonationApi.donationStatus(trackingToken as string),
    enabled: !!trackingToken,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status && FINAL_STATUSES.includes(status) ? false : STATUS_POLL_INTERVAL_MS
    },
    refetchIntervalInBackground: true,
    retry: 2,
  })
}

export function usePublicCollectionsQuery() {
  return useQuery({ queryKey: ['public-collections'], queryFn: publicDonationApi.collections })
}

export function usePublicMonthlyOfferQuery() {
  return useQuery({
    queryKey: ['public-monthly-contribution'],
    queryFn: publicDonationApi.monthlyContribution,
  })
}
