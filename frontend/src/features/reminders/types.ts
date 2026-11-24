// Contrat JSON du module Relances (ReminderController) : contribution mensuelle et collectes relançables (lot 2 bis).

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
  /** Montant habituel de la contribution mensuelle ; null pour une collecte (participation libre). */
  expectedAmount: number | null
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
  /** Bloqué seulement par une règle souple (délai, maximum, ouverture, rappel programmé). */
  canForce: boolean
  blockedCode: string | null
  blockedReason: string | null
  needsAttention: boolean
  lastResponse: ReminderResponseType | null
  promisedDate: string | null
}

export type ReminderResponseType = 'ACK' | 'PROMISE' | 'UNSURE' | 'DONE' | 'REFERENCE' | 'FREE_TEXT'

export type ReminderReason = 'MONTHLY_CONTRIBUTION' | 'PROJECT'

export interface ReminderReasonOption {
  reason: ReminderReason
  month: string | null
  projectId: number | null
  label: string
  dueDate: string | null
}

export interface ReminderOverview {
  /** Mois (« 2026-11 ») ; null pour une collecte. */
  month: string | null
  /** Libellé du motif : « novembre 2026 » ou le nom de la collecte. */
  monthLabel: string
  currentMonth: boolean
  enabled: boolean
  openFrom: string
  dueDate: string
  maxReminders: number
  reason: ReminderReason
  projectId: number | null
  projectNote: string | null
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
  whatsappText: string | null
  channels: { channel: ReminderChannel; recipient: string }[]
  canRemind: boolean
  canForce: boolean
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
  needsAttention: boolean
  promisedDate: string | null
  responses: {
    channel: ReminderChannel
    type: ReminderResponseType
    promisedDate: string | null
    text: string | null
    outcome: string | null
    receivedAt: string
  }[]
  reminders: {
    id: number
    sequenceNo: number
    trigger: ReminderTrigger
    kind: 'RELANCE' | 'PROMISE'
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
  cooldownNoResponseHours: number
  cooldownAckHours: number
  cooldownNotFoundHours: number
  maxPerMonth: number
  autoReminderOnPromise: boolean
  updatedAt: string | null
}

export type ReminderSettingsInput = Omit<ReminderSettings, 'updatedAt'>
