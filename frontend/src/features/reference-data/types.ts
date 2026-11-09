export interface ProjectOption {
  id: number
  name: string
  status: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'
}

export interface CategoryOption {
  id: number
  name: string
  /** Caisse principale (Caisse MLA). */
  mainFund: boolean
}

export interface PaymentChannelOption {
  id: number
  code: string
  label: string
}

export interface DonorOption {
  id: number
  fullName: string | null
  displayName: string | null
  memberRole: 'RESPONSABLE' | 'MEMBRE' | null
}
