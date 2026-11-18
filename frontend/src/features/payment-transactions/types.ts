// Vue back-office des dons publics (PaymentTransaction) — voir features/public pour le formulaire
// côté donateur.

/** PENDING : manuel, à confirmer ; PROCESSING : en ligne (PawaPay), validation en cours sur le téléphone. */
export type PaymentTransactionStatus = 'PENDING' | 'PROCESSING' | 'CONFIRMED' | 'FAILED' | 'CANCELLED'

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
  /** true : payé en ligne via PawaPay (statut piloté par le prestataire, pas de confirmation manuelle). */
  online: boolean
  payerPhone: string | null
  providerStatus: string | null
  failureCode: string | null
  failureMessage: string | null
  /** Sans réponse du prestataire après le délai de réconciliation, ou anomalie : à regarder. */
  needsReview: boolean
  nextCheckAt: string | null
  updatedAt: string | null
  /** DONATION : don à une collecte ; MONTHLY_CONTRIBUTION : contribution mensuelle d'un membre (caisse). */
  kind: PaymentTransactionKind
  /** Membre qui paie (contribution mensuelle). */
  member: MemberRef | null
  /** Mois couverts « YYYY-MM » (contribution mensuelle). */
  months: string[]
  amountPerMonth: number | null
  /** Mois déjà payés par ce membre en dehors de ce don : simple avertissement. */
  alreadyPaidMonths: string[]
}

export type PaymentTransactionKind = 'DONATION' | 'MONTHLY_CONTRIBUTION'

export interface MemberRef {
  id: number
  fullName: string | null
  displayName: string | null
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
