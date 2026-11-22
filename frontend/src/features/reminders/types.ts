// Contrat JSON du module Relances (ReminderController, lot 1 : contribution mensuelle par email).

export type ReminderCaseStatus =
  | 'A_RELANCER'
  | 'EN_ATTENTE_REPONSE'
  | 'ACCUSE'
  | 'PROMIS'
  | 'VERIFICATION'
  | 'DECLARE_NON_RETROUVE'
  | 'REGLE'
  | 'SANS_SUITE'

export type ReminderChannel = 'EMAIL' | 'WHATSAPP'
export type ReminderDeliveryStatus = 'QUEUED' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED'
export type ReminderMonthStatus = 'PAID' | 'UNPAID' | 'IN_PROGRESS'
export type ReminderTrigger = 'MANUAL' | 'BULK' | 'SCHEDULED'

export interface ReminderDeliverySummary {
  channel: ReminderChannel
  status: ReminderDeliveryStatus
}

export interface ReminderRow {
  donorId: number
  label: string
  role: 'RESPONSABLE' | 'MEMBRE'
  expectedAmount: number
  monthStatus: ReminderMonthStatus
  hasEmail: boolean
  hasPhone: boolean
  caseId: number | null
  caseStatus: ReminderCaseStatus | null
  reminderCount: number
  lastReminderAt: string | null
  lastReminderScheduledFor: string | null
  lastDeliveries: ReminderDeliverySummary[]
  nextAllowedAt: string | null
  canRemind: boolean
  blockedCode: string | null
  blockedReason: string | null
  needsAttention: boolean
}

export interface ReminderOverview {
  month: string
  monthLabel: string
  currentMonth: boolean
  enabled: boolean
  openFrom: string
  dueDate: string
  maxReminders: number
  summary: {
    members: number
    upToDate: number
    unpaid: number
    inProgress: number
    remindable: number
    remindersSent: number
    maxReached: number
  }
  rows: ReminderRow[]
}

export interface ReminderPreview {
  subject: string
  html: string
  text: string
  channels: { channel: ReminderChannel; recipient: string }[]
  canRemind: boolean
  blockedReason: string | null
}

export interface ReminderSendResult {
  caseId: number
  sequenceNo: number
  sent: boolean
  queued: boolean
  scheduledFor: string | null
  nextAllowedAt: string | null
  deliveries: {
    channel: ReminderChannel
    recipient: string
    status: ReminderDeliveryStatus
    error: string | null
  }[]
}

export interface ReminderBulkResult {
  sent: { donorId: number; label: string; delivered: boolean; queued: boolean }[]
  skipped: { donorId: number; label: string; reason: string }[]
}

export interface ReminderCaseDetail {
  id: number
  donorId: number
  donorLabel: string
  month: string | null
  monthLabel: string | null
  status: ReminderCaseStatus
  reminderCount: number
  nextAllowedAt: string | null
  createdAt: string
  closedAt: string | null
  closedReason: string | null
  closedBy: string | null
  contributionId: number | null
  reminders: {
    id: number
    sequenceNo: number
    trigger: ReminderTrigger
    sentBy: string | null
    createdAt: string
    scheduledFor: string | null
    sentAt: string | null
    cancelledAt: string | null
    message: string | null
    deliveries: {
      channel: ReminderChannel
      recipient: string
      status: ReminderDeliveryStatus
      error: string | null
      at: string | null
    }[]
  }[]
}

export interface ReminderSettings {
  enabled: boolean
  openFromDay: number
  cooldownNoResponseDays: number
  cooldownAckDays: number
  cooldownNotFoundHours: number
  maxPerMonth: number
  sendWindowStart: string
  sendWindowEnd: string
  autoReminderOnPromise: boolean
  updatedAt: string | null
}

export type ReminderSettingsInput = Omit<ReminderSettings, 'updatedAt'>
