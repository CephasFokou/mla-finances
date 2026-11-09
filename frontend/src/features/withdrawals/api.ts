import { apiClient } from '../../lib/apiClient'
import type {
  PageResponse,
  WithdrawalBalanceResponse,
  WithdrawalCreateInput,
  WithdrawalFilters,
  WithdrawalRejectInput,
  WithdrawalResponse,
} from './types'

function toQueryString(filters: WithdrawalFilters): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value))
    }
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const withdrawalsApi = {
  list: (filters: WithdrawalFilters) =>
    apiClient.get<PageResponse<WithdrawalResponse>>(`/withdrawals${toQueryString(filters)}`),

  get: (id: number) => apiClient.get<WithdrawalResponse>(`/withdrawals/${id}`),

  create: (input: WithdrawalCreateInput) => apiClient.post<WithdrawalResponse>('/withdrawals', input),

  /** Sortie déjà effectuée : enregistrée directement comme décaissée à sa date réelle. */
  record: (input: WithdrawalCreateInput) => apiClient.post<WithdrawalResponse>('/withdrawals/record', input),

  exportCsv: (filters: WithdrawalFilters) =>
    apiClient.get<string>(`/withdrawals/export${toQueryString(filters)}`),

  approve: (id: number) => apiClient.post<WithdrawalResponse>(`/withdrawals/${id}/approve`),

  reject: (id: number, input: WithdrawalRejectInput) =>
    apiClient.post<WithdrawalResponse>(`/withdrawals/${id}/reject`, input),

  cancel: (id: number) => apiClient.post<WithdrawalResponse>(`/withdrawals/${id}/cancel`),

  process: (id: number) => apiClient.post<WithdrawalResponse>(`/withdrawals/${id}/process`),

  projectBalance: (projectId: number) =>
    apiClient.get<WithdrawalBalanceResponse>(`/projects/${projectId}/balance`),

  categoryBalance: (categoryId: number) =>
    apiClient.get<WithdrawalBalanceResponse>(`/categories/${categoryId}/balance`),
}
