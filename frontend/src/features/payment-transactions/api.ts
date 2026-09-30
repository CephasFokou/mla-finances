import { apiClient } from '../../lib/apiClient'
import type {
  PageResponse,
  PaymentTransactionCancelInput,
  PaymentTransactionFilters,
  PaymentTransactionResponse,
} from './types'

function toQueryString(filters: PaymentTransactionFilters): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value))
    }
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const paymentTransactionsApi = {
  list: (filters: PaymentTransactionFilters) =>
    apiClient.get<PageResponse<PaymentTransactionResponse>>(`/payment-transactions${toQueryString(filters)}`),
  confirm: (id: number) => apiClient.post<PaymentTransactionResponse>(`/payment-transactions/${id}/confirm`),
  cancel: (id: number, input: PaymentTransactionCancelInput) =>
    apiClient.post<PaymentTransactionResponse>(`/payment-transactions/${id}/cancel`, input),
}
