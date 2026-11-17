// Types du site public de don — endpoints /api/v1/public/**, sans authentification.

export interface PublicProject {
  id: number
  name: string
  description: string | null
  targetAmount: number | null
  currentBalance: number
  progressPercent: number | null
}

export interface PublicStats {
  totalRaised: number
  contributionCount: number
  activeProjectCount: number
}

export interface PublicPaymentChannel {
  id: number
  code: string
  label: string
  /** true : demande de paiement poussée sur le téléphone (PawaPay) ; false : paiement manuel. */
  online: boolean
}

export interface PublicDonationInput {
  projectId: number
  paymentChannelId: number
  amount: number
  anonymous: boolean
  donorName?: string
  donorContact?: string
  /** Numéro Mobile Money à débiter, requis pour un canal en ligne. */
  payerPhone?: string
}

/** PENDING : manuel, en attente du secrétariat ; PROCESSING : validation sur le téléphone en cours. */
export type PublicDonationStatus = 'PENDING' | 'PROCESSING' | 'CONFIRMED' | 'FAILED' | 'CANCELLED'

export interface PublicDonationResult {
  reference: string
  status: PublicDonationStatus
  mode: 'ONLINE' | 'MANUAL'
  /** Jeton opaque pour suivre le don (GET /public/donations/{token}). */
  trackingToken: string
  failureMessage: string | null
}

export interface PublicDonationStatusResult {
  reference: string
  status: PublicDonationStatus
  failureCode: string | null
  failureMessage: string | null
}

/** Collecte publique : montants agrégés seulement (GET /public/collections). */
export interface PublicCollection {
  id: number
  name: string
  /** OPEN : on peut donner ; UPCOMING : pas encore ouverte ; CLOSED : clôturée. */
  state: 'OPEN' | 'UPCOMING' | 'CLOSED'
  collected: number
  handedOver: number
  contributionsCount: number
  firstMovementDate: string | null
  lastMovementDate: string | null
}
