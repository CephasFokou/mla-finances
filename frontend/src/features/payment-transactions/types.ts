// Vue back-office des dons publics (PaymentTransaction) — voir features/public pour le formulaire
// côté donateur.

export type PaymentTransactionStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED'

export interface RefOption {
  id: number
  name: string
}

export interface PaymentChannelRef {
  id: number
  code: string
  label: string
}

export interface PaymentTransactionResponse {
  id: number
  reference: string
  project: RefOption | null
  category: RefOption | null
  paymentChannel: PaymentChannelRef
  amount: number
  donorName: string | null
  donorContact: string | null
  anonymous: boolean
  provider: string
  status: PaymentTransactionStatus
  contributionId: number | null
  cancelReason: string | null
  createdAt: string
  confirmedAt: string | null
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface PaymentTransactionFilters {
  status?: PaymentTransactionStatus
  page?: number
  size?: number
}

export interface PaymentTransactionCancelInput {
  reason: string
}
