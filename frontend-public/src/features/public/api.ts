import { apiClient } from '../../lib/apiClient'
import type {
  PublicCollection,
  PublicDonationInput,
  PublicDonationResult,
  PublicDonationStatusResult,
  PublicMonthlyContributionInput,
  PublicMonthlyOffer,
  PublicPaymentChannel,
  PublicProject,
  PublicStats,
} from './types'

export const publicDonationApi = {
  projects: () => apiClient.get<PublicProject[]>('/public/projects'),
  paymentChannels: () => apiClient.get<PublicPaymentChannel[]>('/public/payment-channels'),
  stats: () => apiClient.get<PublicStats>('/public/stats'),
  collections: () => apiClient.get<PublicCollection[]>('/public/collections'),
  monthlyContribution: () => apiClient.get<PublicMonthlyOffer>('/public/monthly-contribution'),
  donate: (input: PublicDonationInput | PublicMonthlyContributionInput) =>
    apiClient.post<PublicDonationResult>('/public/donations', input),
  donationStatus: (trackingToken: string) =>
    apiClient.get<PublicDonationStatusResult>(`/public/donations/${encodeURIComponent(trackingToken)}`),
}
