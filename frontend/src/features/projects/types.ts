// Types calqués sur le contrat JSON du module "Projets & catégories" (backend ProjectController /
// CategoryController). Toute évolution du contrat backend doit être répercutée ici.

export type ProjectStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'

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
}

export interface ProjectUpdateInput {
  name: string
  description?: string
  targetAmount?: number
  startDate?: string
  endDate?: string
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
