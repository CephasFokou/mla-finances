import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { publicDonationApi } from './api'
import type { PublicDonationInput } from './types'

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
    mutationFn: (input: PublicDonationInput) => publicDonationApi.donate(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['public-collections'] }),
  })
}

export function usePublicCollectionsQuery() {
  return useQuery({ queryKey: ['public-collections'], queryFn: publicDonationApi.collections })
}
