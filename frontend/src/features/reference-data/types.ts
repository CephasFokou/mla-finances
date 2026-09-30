export interface ProjectOption {
  id: number
  name: string
  status: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'
}

export interface CategoryOption {
  id: number
  name: string
}

export interface PaymentChannelOption {
  id: number
  code: string
  label: string
}

export interface DonorOption {
  id: number
  fullName: string
}
