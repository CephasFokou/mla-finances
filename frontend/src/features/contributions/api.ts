import { apiClient } from '../../lib/apiClient'
import type {
  BalanceResponse,
  ContributionCancelInput,
  ContributionCreateInput,
  ContributionFilters,
  ContributionResponse,
  ContributionUpdateInput,
  PageResponse,
} from './types'

function toQueryString(filters: ContributionFilters): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value))
    }
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const contributionsApi = {
  list: (filters: ContributionFilters) =>
    apiClient.get<PageResponse<ContributionResponse>>(`/contributions${toQueryString(filters)}`),

  get: (id: number) => apiClient.get<ContributionResponse>(`/contributions/${id}`),

  create: (input: ContributionCreateInput) => apiClient.post<ContributionResponse>('/contributions', input),

  update: (id: number, input: ContributionUpdateInput) =>
    apiClient.put<ContributionResponse>(`/contributions/${id}`, input),

  cancel: (id: number, input: ContributionCancelInput) =>
    apiClient.post<ContributionResponse>(`/contributions/${id}/cancel`, input),

  exportCsv: (filters: ContributionFilters) =>
    apiClient.get<string>(`/contributions/export${toQueryString(filters)}`),

  projectBalance: (projectId: number) => apiClient.get<BalanceResponse>(`/projects/${projectId}/balance`),

  categoryBalance: (categoryId: number) =>
    apiClient.get<BalanceResponse>(`/categories/${categoryId}/balance`),
}
