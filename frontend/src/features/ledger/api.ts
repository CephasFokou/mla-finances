import { apiClient } from '../../lib/apiClient'
import type {
  Activity,
  ActivityInput,
  ActivityReportRow,
  CollectionRow,
  LedgerSummary,
  Member,
  MemberInput,
  MonthlyCalendar,
  PaymentChannel,
  PaymentChannelInput,
  TransferInput,
  TransferResult,
} from './types'

export const ledgerApi = {
  summary: (year: number) => apiClient.get<LedgerSummary>(`/reports/summary?year=${year}`),
  calendar: (year: number) => apiClient.get<MonthlyCalendar>(`/reports/monthly-calendar?year=${year}`),
  collections: () => apiClient.get<CollectionRow[]>('/reports/collections'),
  activityReport: () => apiClient.get<ActivityReportRow[]>('/reports/activities'),

  activities: () => apiClient.get<Activity[]>('/activities'),
  createActivity: (input: ActivityInput) => apiClient.post<Activity>('/activities', input),
  updateActivity: (id: number, input: ActivityInput) => apiClient.put<Activity>(`/activities/${id}`, input),

  members: () => apiClient.get<Member[]>('/members'),
  createMember: (input: MemberInput) => apiClient.post<Member>('/members', input),
  updateMember: (id: number, input: MemberInput) => apiClient.put<Member>(`/members/${id}`, input),

  createTransfer: (input: TransferInput) => apiClient.post<TransferResult>('/transfers', input),

  allPaymentChannels: () => apiClient.get<PaymentChannel[]>('/payment-channels/all'),
  createPaymentChannel: (input: PaymentChannelInput) =>
    apiClient.post<PaymentChannel>('/payment-channels', input),
  updatePaymentChannel: (id: number, input: PaymentChannelInput) =>
    apiClient.put<PaymentChannel>(`/payment-channels/${id}`, input),
  setPaymentChannelActive: (id: number, active: boolean) =>
    apiClient.post<PaymentChannel>(`/payment-channels/${id}/${active ? 'activate' : 'deactivate'}`),
}
