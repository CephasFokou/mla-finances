import { useMutation, useQuery } from '@tanstack/react-query'

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
  return useMutation({ mutationFn: (input: PublicDonationInput) => publicDonationApi.donate(input) })
}
