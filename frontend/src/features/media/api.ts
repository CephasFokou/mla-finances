import { apiClient, BASE_URL } from '../../lib/apiClient'
import type { MediaAsset, MediaCategory, MediaReorderInput, MediaUploadInput } from './types'

export const mediaApi = {
  list: (category: MediaCategory, includeInactive = false) =>
    apiClient.get<MediaAsset[]>(`/media?category=${category}&includeInactive=${includeInactive}`),

  listByProject: (projectId: number, includeInactive = false) =>
    apiClient.get<MediaAsset[]>(`/media/project/${projectId}?includeInactive=${includeInactive}`),

  upload: (input: MediaUploadInput) => {
    const formData = new FormData()
    formData.append('category', input.category)
    formData.append('file', input.file)
    if (input.title) formData.append('title', input.title)
    if (input.projectId !== undefined) formData.append('projectId', String(input.projectId))
    return apiClient.upload<MediaAsset>('/media', formData)
  },

  activate: (id: number) => apiClient.post<MediaAsset>(`/media/${id}/activate`),

  deactivate: (id: number) => apiClient.post<MediaAsset>(`/media/${id}/deactivate`),

  reorderBanners: (input: MediaReorderInput) => apiClient.put<void>('/media/banners/order', input),
}

// Une image de bannière/visuel/bibliothèque se charge directement dans une balise <img> — pas de
// jeton d'autorisation à ce stade (voir la note dans MediaController), donc une simple URL suffit.
export function mediaFileUrl(id: number): string {
  return `${BASE_URL}/media/${id}/file`
}
