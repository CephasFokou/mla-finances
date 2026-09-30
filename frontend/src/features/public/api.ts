import { apiClient } from '../../lib/apiClient'
import type {
  PublicDonationInput,
  PublicDonationResult,
  PublicPaymentChannel,
  PublicProject,
  PublicStats,
} from './types'

export const publicDonationApi = {
  projects: () => apiClient.get<PublicProject[]>('/public/projects'),
  paymentChannels: () => apiClient.get<PublicPaymentChannel[]>('/public/payment-channels'),
  stats: () => apiClient.get<PublicStats>('/public/stats'),
  donate: (input: PublicDonationInput) => apiClient.post<PublicDonationResult>('/public/donations', input),
}
