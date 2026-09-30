import { apiClient } from '../../lib/apiClient'
import type {
  AuditLogFilters,
  AuditLogEntry,
  PageResponse,
  ProjectMonthlyReport,
  ReportsOverview,
} from './types'

function toQueryString(filters: AuditLogFilters): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value))
    }
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const reportsApi = {
  overview: () => apiClient.get<ReportsOverview>('/reports/overview'),

  projectMonthly: (projectId: number, months: number) =>
    apiClient.get<ProjectMonthlyReport>(`/reports/projects/${projectId}/monthly?months=${months}`),

  auditLog: (filters: AuditLogFilters) =>
    apiClient.get<PageResponse<AuditLogEntry>>(`/reports/audit${toQueryString(filters)}`),
}
