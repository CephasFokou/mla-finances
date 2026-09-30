// Types calqués sur le contrat JSON du module Retraits (backend WithdrawalController).

export type WithdrawalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'PROCESSED' | 'CANCELLED'

export interface RefOption {
  id: number
  name: string
}

export interface WithdrawalResponse {
  id: number
  project: RefOption | null
  category: RefOption | null
  amount: number
  reason: string
  status: WithdrawalStatus
  requestedAt: string
  decidedAt: string | null
  rejectionReason: string | null
  processedAt: string | null
  reference: string | null
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface WithdrawalFilters {
  projectId?: number
  categoryId?: number
  status?: WithdrawalStatus
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
  sort?: string
}

export interface WithdrawalCreateInput {
  projectId?: number
  categoryId?: number
  amount: number
  reason: string
}

export interface WithdrawalRejectInput {
  reason: string
}

// Même forme que BalanceResponse (features/contributions/types.ts), étendue avec les champs de
// solde réel/prévisionnel introduits par le module Retraits (voir BalanceService côté backend).
export interface WithdrawalBalanceResponse {
  projectId?: number
  categoryId?: number
  openingBalance: number
  totalContributions: number
  totalWithdrawals: number
  currentBalance: number
  engagedAmount: number
  provisionalBalance: number
  deficit: boolean
  contributionCount: number
  pendingWithdrawalCount: number
}
