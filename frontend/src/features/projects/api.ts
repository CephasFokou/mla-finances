import { apiClient } from '../../lib/apiClient'
import type {
  Category,
  CategoryInput,
  Project,
  ProjectCreateInput,
  ProjectOccurrenceInput,
  ProjectStatus,
  ProjectUpdateInput,
} from './types'

export const projectsApi = {
  list: (status?: ProjectStatus) => apiClient.get<Project[]>(`/projects${status ? `?status=${status}` : ''}`),

  get: (id: number) => apiClient.get<Project>(`/projects/${id}`),

  occurrencesOfSeries: (seriesId: string) =>
    apiClient.get<Project[]>(`/projects/series/${encodeURIComponent(seriesId)}`),

  create: (input: ProjectCreateInput) => apiClient.post<Project>('/projects', input),

  update: (id: number, input: ProjectUpdateInput) => apiClient.put<Project>(`/projects/${id}`, input),

  activate: (id: number) => apiClient.post<Project>(`/projects/${id}/activate`),

  complete: (id: number) => apiClient.post<Project>(`/projects/${id}/complete`),

  archive: (id: number) => apiClient.post<Project>(`/projects/${id}/archive`),

  duplicateOccurrence: (id: number, input: ProjectOccurrenceInput) =>
    apiClient.post<Project>(`/projects/${id}/duplicate-occurrence`, input),
}

export const categoriesApi = {
  listAll: () => apiClient.get<Category[]>('/categories?includeInactive=true'),

  create: (input: CategoryInput) => apiClient.post<Category>('/categories', input),

  update: (id: number, input: CategoryInput) => apiClient.put<Category>(`/categories/${id}`, input),

  activate: (id: number) => apiClient.post<Category>(`/categories/${id}/activate`),

  deactivate: (id: number) => apiClient.post<Category>(`/categories/${id}/deactivate`),
}
