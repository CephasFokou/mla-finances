import { apiClient } from '../../lib/apiClient'
import type {
  MobileMoneyBalance,
  PageResponse,
  PayoutOptions,
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

  /** Décaissement Mobile Money vers un numéro MLA de la liste fermée (payout wallet-ledger). */
  payout: (id: number, recipientKey: string) =>
    apiClient.post<WithdrawalResponse>(`/withdrawals/${id}/payout`, { recipientKey }),

  /** Bon de décaissement PDF (retrait décaissé uniquement), produit par le serveur. */
  voucher: (id: number) => apiClient.download(`/withdrawals/${id}/voucher.pdf`),

  recheckPayout: (id: number) => apiClient.post<WithdrawalResponse>(`/withdrawals/${id}/payout/recheck`),

  payoutOptions: () => apiClient.get<PayoutOptions>('/withdrawals/payout-options'),

  mobileMoneyBalance: () => apiClient.get<MobileMoneyBalance>('/withdrawals/mobile-money-balance'),

  projectBalance: (projectId: number) =>
    apiClient.get<WithdrawalBalanceResponse>(`/projects/${projectId}/balance`),

  categoryBalance: (categoryId: number) =>
    apiClient.get<WithdrawalBalanceResponse>(`/categories/${categoryId}/balance`),
}
