// Types calqués sur le contrat JSON du module Retraits (backend WithdrawalController).

import type { ActivityRef, MovementNature } from '../ledger/types'
import type { DonorRef, PaymentChannelRef } from '../contributions/types'

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
  nature: MovementNature
  person: DonorRef | null
  paymentChannel: PaymentChannelRef | null
  activity: ActivityRef | null
  operationDate: string | null
  remark: string | null
  transferGroup: string | null
  status: WithdrawalStatus
  requestedAt: string
  requestedByName: string | null
  decidedAt: string | null
  decidedByName: string | null
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
  nature?: MovementNature
  activityId?: number
  personId?: number
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
  nature?: MovementNature
  personId?: number
  paymentChannelId?: number
  activityId?: number
  /** Renseignée uniquement pour une sortie déjà effectuée (POST /withdrawals/record). */
  operationDate?: string
  remark?: string
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
