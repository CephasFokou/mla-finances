// Types calqués sur le contrat JSON du module "Projets & catégories" (backend ProjectController /
// CategoryController). Toute évolution du contrat backend doit être répercutée ici.

export type ProjectStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'

/** Membres concernés par les relances d'une collecte (cadrage Relances §6 bis). */
export type ProjectReminderAudience = 'ALL' | 'RESPONSABLES' | 'MEMBRES' | 'SELECTION'

/** Bloc « Relances » d'une collecte : pas de montant, la participation est libre. */
export interface ProjectReminders {
  enabled: boolean
  audience: ProjectReminderAudience
  openFrom: string | null // "YYYY-MM-DD"
  dueDate: string | null // "YYYY-MM-DD"
  message: string | null
  memberIds: number[]
}

export interface ProjectRemindersInput {
  enabled: boolean
  audience: ProjectReminderAudience
  openFrom?: string
  dueDate?: string
  message?: string
  memberIds?: number[]
}

export interface Project {
  id: number
  name: string
  description: string | null
  status: ProjectStatus
  openingBalance: number
  targetAmount: number | null
  recurringSeriesId: string | null
  startDate: string | null // "YYYY-MM-DD"
  endDate: string | null // "YYYY-MM-DD"
  createdAt: string
  updatedAt: string
  reminders: ProjectReminders
}

// Le solde d'ouverture n'est plus modifiable une fois le projet créé (comme le montant d'une
// contribution, RG-17) : à corriger par archivage + recréation si une erreur de saisie survient.
export interface ProjectCreateInput {
  name: string
  description?: string
  openingBalance: number
  targetAmount?: number
  recurringSeriesId?: string
  startDate?: string
  endDate?: string
  reminders?: ProjectRemindersInput
}

export interface ProjectUpdateInput {
  name: string
  description?: string
  targetAmount?: number
  startDate?: string
  endDate?: string
  /** Absent : bloc « Relances » inchangé. */
  reminders?: ProjectRemindersInput
}

export interface ProjectOccurrenceInput {
  name: string
  description?: string
  openingBalance: number
  targetAmount?: number
  startDate?: string
  endDate?: string
}

export interface Category {
  id: number
  name: string
  description: string | null
  active: boolean
}

export interface CategoryInput {
  name: string
  description?: string
}
