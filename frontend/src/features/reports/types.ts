// Types calqués sur le contrat JSON du module Rapports (backend ReportsController).

export interface ReportsOverview {
  globalBalance: number
  mobilizedThisMonth: number
  disbursedThisMonth: number
  pendingWithdrawalsCount: number
  generatedAt: string
}

export interface MonthlyAmount {
  month: string
  contributions: number
  withdrawals: number
}

export interface RefOption {
  id: number
  name: string
}

export interface ProjectMonthlyReport {
  project: RefOption | null
  months: MonthlyAmount[]
}

export type AuditEntityType = 'CONTRIBUTION' | 'WITHDRAWAL' | 'PROJECT' | 'CATEGORY'

export type AuditAction =
  'CREATE' | 'APPROVE' | 'REJECT' | 'CANCEL' | 'PROCESS' | 'ACTIVATE' | 'COMPLETE' | 'ARCHIVE' | 'DEACTIVATE'

export interface AuditLogEntry {
  id: number
  entityType: AuditEntityType
  entityId: number
  action: AuditAction
  actorName: string | null
  summary: string
  occurredAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface AuditLogFilters {
  entityType?: AuditEntityType
  action?: AuditAction
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
  sort?: string
}
