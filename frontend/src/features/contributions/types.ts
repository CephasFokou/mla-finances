// Types calqués sur le contrat JSON défini dans backend/docs/module-contributions-spec.md (§6).
// Toute évolution du contrat backend doit être répercutée ici.

import type { ActivityRef, MemberRole, MovementNature } from '../ledger/types'

export type MovementType = 'STANDARD' | 'TRANSFER' | 'ADJUSTMENT'

export interface RefOption {
  id: number
  name: string
}

export interface DonorRef {
  id: number
  fullName: string | null
  /** Nom usuel (« Maman Jeanne ») : c'est le nom à afficher. */
  displayName: string | null
  memberRole: MemberRole | null
}

export interface PaymentChannelRef {
  id: number
  code: string
  label: string
}

export interface ContributionResponse {
  id: number
  project: RefOption | null
  category: RefOption | null
  donor: DonorRef | null
  paymentChannel: PaymentChannelRef
  amount: number
  periodMonth: string // "YYYY-MM-DD" (normalisé au 1er du mois)
  transactionDate: string // "YYYY-MM-DD"
  movementType: MovementType
  nature: MovementNature
  activity: ActivityRef | null
  remark: string | null
  transferGroup: string | null
  anonymous: boolean
  reference: string | null
  notes: string | null
  adjustsContributionId: number | null
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ContributionFilters {
  projectId?: number
  categoryId?: number
  donorId?: number
  paymentChannelId?: number
  movementType?: MovementType
  nature?: MovementNature
  activityId?: number
  anonymous?: boolean
  periodMonth?: string // "YYYY-MM"
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
  sort?: string
}

export interface ContributionCreateInput {
  projectId?: number
  categoryId?: number
  donorId?: number
  paymentChannelId: number
  amount: number
  periodMonth: string // "YYYY-MM"
  transactionDate: string
  anonymous: boolean
  notes?: string
  nature?: MovementNature
  activityId?: number
  remark?: string
}

export interface ContributionUpdateInput {
  donorId?: number | null
  anonymous?: boolean
  notes?: string
  remark?: string | null
  activityId?: number | null
}

export interface ContributionCancelInput {
  reason: string
}

export interface BalanceResponse {
  projectId?: number
  categoryId?: number
  openingBalance: number
  totalContributions: number
  currentBalance: number
  contributionCount: number
}
