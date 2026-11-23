import { apiClient } from '../../lib/apiClient'
import type {
  ContributionLink,
  ReminderAction,
  ReminderActionResult,
  ReminderActionView,
  PublicCollection,
  PublicDonationInput,
  PublicDonationResult,
  PublicDonationStatusResult,
  PublicMonthlyContributionInput,
  PublicMonthlyOffer,
  PublicPaymentChannel,
  PublicProject,
  PublicStats,
} from './types'

export const publicDonationApi = {
  projects: () => apiClient.get<PublicProject[]>('/public/projects'),
  paymentChannels: () => apiClient.get<PublicPaymentChannel[]>('/public/payment-channels'),
  stats: () => apiClient.get<PublicStats>('/public/stats'),
  collections: () => apiClient.get<PublicCollection[]>('/public/collections'),
  monthlyContribution: () => apiClient.get<PublicMonthlyOffer>('/public/monthly-contribution'),
  donate: (input: PublicDonationInput | PublicMonthlyContributionInput) =>
    apiClient.post<PublicDonationResult>('/public/donations', input),
  contributionLink: (token: string) =>
    apiClient.get<ContributionLink>(`/public/contribution-link/${encodeURIComponent(token)}`),
  reminderAction: (token: string) =>
    apiClient.get<ReminderActionView>(`/public/reminder-actions/${encodeURIComponent(token)}`),
  answerReminder: (token: string, action: ReminderAction, date?: string) =>
    apiClient.post<ReminderActionResult>(`/public/reminder-actions/${encodeURIComponent(token)}`, {
      action,
      date,
    }),
  donationStatus: (trackingToken: string) =>
    apiClient.get<PublicDonationStatusResult>(`/public/donations/${encodeURIComponent(trackingToken)}`),
}
