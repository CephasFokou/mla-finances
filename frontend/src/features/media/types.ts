// Types calqués sur le contrat JSON du module Médias (MediaController).

export type MediaCategory = 'BANNER' | 'PROJECT_VISUAL' | 'LIBRARY'

export interface MediaProjectRef {
  id: number
  name: string
}

export interface MediaAsset {
  id: number
  category: MediaCategory
  title: string | null
  fileName: string
  contentType: string
  sizeBytes: number
  project: MediaProjectRef | null
  displayOrder: number | null
  active: boolean
  uploadedAt: string
}

export interface MediaUploadInput {
  category: MediaCategory
  file: File
  title?: string
  projectId?: number
}

export interface MediaReorderInput {
  orderedIds: number[]
}
