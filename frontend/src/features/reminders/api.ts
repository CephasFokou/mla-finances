import { apiClient } from '../../lib/apiClient'
import type {
  ReminderBulkResult,
  ReminderCaseDetail,
  ReminderOverview,
  ReminderPreview,
  ReminderReasonOption,
  ReminderSendResult,
  ReminderSettings,
  ReminderSettingsInput,
} from './types'

/**
 * Motif d'une relance côté écran : « 2026-11 » (contribution mensuelle de ce mois) ou « P12 » (collecte n° 12).
 */
export function motiveBody(motive: string): { month: string } | { projectId: number } {
  return motive.startsWith('P') ? { projectId: Number(motive.slice(1)) } : { month: motive }
}

function motiveQuery(motive: string): string {
  return motive.startsWith('P')
    ? `projectId=${Number(motive.slice(1))}`
    : `month=${encodeURIComponent(motive)}`
}

export const remindersApi = {
  reasons: () => apiClient.get<ReminderReasonOption[]>('/reminders/reasons'),
  overview: (motive: string) => apiClient.get<ReminderOverview>(`/reminders?${motiveQuery(motive)}`),
  preview: (donorId: number, motive: string) =>
    apiClient.get<ReminderPreview>(`/reminders/preview?donorId=${donorId}&${motiveQuery(motive)}`),
  detail: (caseId: number) => apiClient.get<ReminderCaseDetail>(`/reminders/cases/${caseId}`),
  /** force : relancer malgré un blocage souple ; delayMinutes > 0 : relance programmée. */
  send: (donorId: number, motive: string, options: { force?: boolean; delayMinutes?: number } = {}) =>
    apiClient.post<ReminderSendResult>('/reminders', { donorId, ...motiveBody(motive), ...options }),
  bulk: (donorIds: number[], motive: string, delayMinutes = 0) =>
    apiClient.post<ReminderBulkResult>('/reminders/bulk', { donorIds, ...motiveBody(motive), delayMinutes }),
  reset: (caseId: number) => apiClient.post<ReminderCaseDetail>(`/reminders/cases/${caseId}/reset`),
  closeForMember: (donorId: number, motive: string, reason: string) =>
    apiClient.post<ReminderCaseDetail>('/reminders/close', { donorId, ...motiveBody(motive), reason }),
  acknowledge: (caseId: number) =>
    apiClient.post<ReminderCaseDetail>(`/reminders/cases/${caseId}/acknowledge`),
  attention: () => apiClient.get<{ count: number }>('/reminders/attention'),
  settings: () => apiClient.get<ReminderSettings>('/reminder-settings'),
  updateSettings: (input: ReminderSettingsInput) =>
    apiClient.put<ReminderSettings>('/reminder-settings', input),
}
