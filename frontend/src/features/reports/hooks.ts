import { useQuery } from '@tanstack/react-query'

import { reportsApi } from './api'
import type { AuditLogFilters } from './types'

// L'aperçu global agrège des sommes qui bougent à chaque mutation (contribution, retrait...) —
// un staleTime court plutôt que le défaut de react-query évite d'afficher des chiffres périmés
// trop longtemps après une action ailleurs dans l'application, sans pour autant relire à chaque rendu.
const OVERVIEW_STALE_TIME = 15_000

export function useReportsOverviewQuery() {
  return useQuery({
    queryKey: ['reports', 'overview'],
    queryFn: reportsApi.overview,
    staleTime: OVERVIEW_STALE_TIME,
  })
}

export function useProjectMonthlyQuery(projectId: number | undefined, months = 6) {
  return useQuery({
    queryKey: ['reports', 'project-monthly', projectId, months],
    queryFn: () => reportsApi.projectMonthly(projectId as number, months),
    enabled: projectId !== undefined,
  })
}

export function useAuditLogQuery(filters: AuditLogFilters) {
  return useQuery({
    queryKey: ['reports', 'audit', filters],
    queryFn: () => reportsApi.auditLog(filters),
    placeholderData: (previous) => previous,
  })
}
