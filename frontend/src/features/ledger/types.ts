// Types calqués sur le contrat JSON des modules « grand livre » du backend : natures des
// mouvements, membres, activités, transferts, canaux de paiement et rapports qui reprennent les
// onglets du fichier de suivi (Synthèse, Calendrier, Collectes, Activités).

export type MovementNature =
  | 'MONTHLY_CONTRIBUTION'
  | 'CONTRIBUTION'
  | 'DONATION'
  | 'RETURN'
  | 'ADVANCE'
  | 'EXPENSE'
  | 'HANDOVER'
  | 'CASH_TOP_UP'
  | 'REPAYMENT'
  | 'FEE'
  | 'TRANSFER'
  | 'REGULARIZATION'

/** Natures proposées à la saisie d'une entrée (les transferts passent par l'écran Transfert). */
export const INFLOW_NATURES: MovementNature[] = [
  'MONTHLY_CONTRIBUTION',
  'CONTRIBUTION',
  'DONATION',
  'RETURN',
  'ADVANCE',
  'REGULARIZATION',
]

/** Natures proposées à la saisie d'une sortie. */
export const OUTFLOW_NATURES: MovementNature[] = [
  'EXPENSE',
  'HANDOVER',
  'CASH_TOP_UP',
  'REPAYMENT',
  'FEE',
  'REGULARIZATION',
]

export type MemberRole = 'RESPONSABLE' | 'MEMBRE'
export type MemberStatus = 'ACTIVE' | 'LEFT' | 'NOT_INTEGRATED'
export type ActivityStatus = 'PLANNED' | 'COMPLETED'

export interface ActivityRef {
  id: number
  name: string
}

export interface Activity {
  id: number
  name: string
  periodLabel: string | null
  eventDate: string | null
  status: ActivityStatus
  comment: string | null
}

export interface ActivityInput {
  name: string
  periodLabel?: string
  eventDate?: string
  status: ActivityStatus
  comment?: string
}

export interface Member {
  id: number
  displayName: string
  firstName: string | null
  lastName: string | null
  fullName: string | null
  memberRole: MemberRole | null
  memberStatus: MemberStatus | null
  email: string | null
  phone: string | null
  remark: string | null
}

export interface MemberInput {
  displayName: string
  firstName?: string
  lastName?: string
  memberRole: MemberRole | null
  memberStatus?: MemberStatus | null
  email?: string
  phone?: string
  remark?: string
}

export interface PaymentChannel {
  id: number
  code: string
  label: string
  active: boolean
  sortOrder: number
}

export interface PaymentChannelInput {
  code?: string
  label: string
  sortOrder?: number
}

export interface TransferInput {
  fromProjectId?: number
  fromCategoryId?: number
  toProjectId?: number
  toCategoryId?: number
  amount: number
  operationDate: string
  reason: string
  activityId?: number
}

export interface TransferResult {
  transferGroup: string
  outflow: { id: number }
  inflow: { id: number }
}

export interface MonthSummary {
  month: string // "2026-03"
  responsablesContributors: number
  responsablesAmount: number
  membersContributors: number
  membersAmount: number
  unattributedAmount: number
  total: number
  responsablesShare: number | null
  mainFundBalanceEndOfMonth: number | null
}

export interface LedgerSummary {
  year: number
  mainFund: { id: number; name: string } | null
  mainFundBalance: number
  openCollectionsBalance: number
  openCollectionsCount: number
  monthlyContributionsTotal: number
  activeResponsablesCount: number
  activeMembersCount: number
  months: MonthSummary[]
  yearTotals: Omit<MonthSummary, 'month' | 'mainFundBalanceEndOfMonth'>
  generatedAt: string
}

export interface CalendarRow {
  donorId: number
  displayName: string
  memberRole: MemberRole
  memberStatus: MemberStatus | null
  amounts: number[]
  total: number
  monthsCount: number
}

export interface MonthlyCalendar {
  year: number
  rows: CalendarRow[]
  unattributed: number[]
  totals: number[]
}

export interface CollectionRow {
  project: { id: number; name: string }
  status: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'
  description: string | null
  firstMovementDate: string | null
  lastMovementDate: string | null
  contributionsCount: number
  collected: number
  otherInflows: number
  used: number
  remaining: number
  engaged: number
}

export interface ActivityReportRow {
  activity: Activity
  expenses: number
  returns: number
  netCost: number
  movementsCount: number
}
