import { apiClient } from '../../lib/apiClient'
import type { CategoryOption, DonorOption, PaymentChannelOption, ProjectOption } from './types'

export const referenceDataApi = {
  projects: () => apiClient.get<ProjectOption[]>('/projects'),
  categories: () => apiClient.get<CategoryOption[]>('/categories'),
  paymentChannels: () => apiClient.get<PaymentChannelOption[]>('/payment-channels'),
  searchDonors: (q: string) =>
    apiClient.get<DonorOption[]>(`/donors${q ? `?q=${encodeURIComponent(q)}` : ''}`),
}
