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
}

export interface PublicDonationInput {
  projectId: number
  paymentChannelId: number
  amount: number
  anonymous: boolean
  donorName?: string
  donorContact?: string
}

export interface PublicDonationResult {
  reference: string
  status: string
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
