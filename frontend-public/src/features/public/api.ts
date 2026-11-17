import { apiClient } from '../../lib/apiClient'
import type {
  PublicCollection,
  PublicDonationInput,
  PublicDonationResult,
  PublicDonationStatusResult,
  PublicPaymentChannel,
  PublicProject,
  PublicStats,
} from './types'

export const publicDonationApi = {
  projects: () => apiClient.get<PublicProject[]>('/public/projects'),
  paymentChannels: () => apiClient.get<PublicPaymentChannel[]>('/public/payment-channels'),
  stats: () => apiClient.get<PublicStats>('/public/stats'),
  collections: () => apiClient.get<PublicCollection[]>('/public/collections'),
  donate: (input: PublicDonationInput) => apiClient.post<PublicDonationResult>('/public/donations', input),
  donationStatus: (trackingToken: string) =>
    apiClient.get<PublicDonationStatusResult>(`/public/donations/${encodeURIComponent(trackingToken)}`),
}
