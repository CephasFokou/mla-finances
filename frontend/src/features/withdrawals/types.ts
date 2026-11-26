// Types calqués sur le contrat JSON du module Retraits (backend WithdrawalController).

import type { ActivityRef, MovementNature } from '../ledger/types'
import type { DonorRef, PaymentChannelRef } from '../contributions/types'

export type WithdrawalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'PROCESSED' | 'CANCELLED'

/** Décaissement Mobile Money (payout wallet-ledger) : distinct du statut du retrait. */
export type PayoutStatus = 'PROCESSING' | 'PENDING_APPROVAL' | 'COMPLETED' | 'FAILED'

/** Codes opérateur PawaPay des destinataires approuvés. */
export type PayoutProvider = 'ORANGE_CMR' | 'MTN_MOMO_CMR'

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
  requestedById: number | null
  requestedByName: string | null
  decidedAt: string | null
  decidedByName: string | null
  rejectionReason: string | null
  processedAt: string | null
  reference: string | null
  payoutId: string | null
  payoutStatus: PayoutStatus | null
  /** Toujours masqué (« 6** ** 28 82 »). */
  payoutPhoneMasked: string | null
  payoutProvider: PayoutProvider | null
  payoutFailureCode: string | null
  payoutRequestedAt: string | null
  payoutCompletedAt: string | null
  payoutRequestedByName: string | null
  /** Référence de la transaction chez l'opérateur (payout reçu), si communiquée. */
  payoutProviderRef: string | null
  /** Saisi et validé par la même personne. */
  selfApproved: boolean
}

export interface PayoutRecipient {
  key: string
  label: string
  provider: PayoutProvider
  maskedPhone: string
}

/** enabled=false en mode manuel (PAWAPAY_ENABLED=false) : l'action n'est pas proposée. */
export interface PayoutOptions {
  enabled: boolean
  recipients: PayoutRecipient[]
}

export interface MobileMoneyBalance {
  enabled: boolean
  /** false si wallet-ledger n'a pas répondu (montants alors absents). */
  reachable: boolean
  currency: string | null
  available: number | null
  reserved: number | null
  asOf: string | null
  /** Ce que MLA Finance attend depuis la bascule sur la Trésorerie (null : contrôle indisponible). */
  expected: number | null
  /** Solde Trésorerie (disponible + réservé) moins l'attendu ; 0 quand tout concorde. */
  gap: number | null
  checkSince: string | null
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
  /** true : seulement les retraits saisis et validés par la même personne. */
  selfApproved?: boolean
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
/**
 * Collecte proposée au formulaire de sortie : ouverte, ou clôturée avec un reste (remainingBalance,
 * plafond de la sortie côté serveur ; null pour une collecte ouverte).
 */
export interface WithdrawableProject {
  id: number
  name: string
  status: 'ACTIVE' | 'COMPLETED'
  remainingBalance: number | null
}

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
