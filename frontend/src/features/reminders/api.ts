import { apiClient } from '../../lib/apiClient'
import type {
  ReminderBulkResult,
  ReminderCaseDetail,
  ReminderOverview,
  ReminderPreview,
  ReminderSendResult,
  ReminderSettings,
  ReminderSettingsInput,
} from './types'

export const remindersApi = {
  overview: (month: string) =>
    apiClient.get<ReminderOverview>(`/reminders?month=${encodeURIComponent(month)}`),
  preview: (donorId: number, month: string) =>
    apiClient.get<ReminderPreview>(
      `/reminders/preview?donorId=${donorId}&month=${encodeURIComponent(month)}`,
    ),
  detail: (caseId: number) => apiClient.get<ReminderCaseDetail>(`/reminders/cases/${caseId}`),
  send: (donorId: number, month: string) =>
    apiClient.post<ReminderSendResult>('/reminders', { donorId, month }),
  bulk: (donorIds: number[], month: string) =>
    apiClient.post<ReminderBulkResult>('/reminders/bulk', { donorIds, month }),
  closeForMember: (donorId: number, month: string, reason: string) =>
    apiClient.post<ReminderCaseDetail>('/reminders/close', { donorId, month, reason }),
  settings: () => apiClient.get<ReminderSettings>('/reminder-settings'),
  updateSettings: (input: ReminderSettingsInput) =>
    apiClient.put<ReminderSettings>('/reminder-settings', input),
}
