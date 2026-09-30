import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { mediaApi } from './api'
import type { MediaCategory, MediaReorderInput, MediaUploadInput } from './types'

export function useMediaQuery(category: MediaCategory, includeInactive: boolean) {
  return useQuery({
    queryKey: ['media', category, includeInactive],
    queryFn: () => mediaApi.list(category, includeInactive),
  })
}

export function useProjectVisualsQuery(projectId: number | undefined, includeInactive: boolean) {
  return useQuery({
    queryKey: ['media', 'project', projectId, includeInactive],
    queryFn: () => mediaApi.listByProject(projectId as number, includeInactive),
    enabled: projectId !== undefined,
  })
}

function useInvalidateMedia() {
  const queryClient = useQueryClient()
  return () => void queryClient.invalidateQueries({ queryKey: ['media'] })
}

export function useUploadMediaMutation() {
  const invalidate = useInvalidateMedia()
  return useMutation({
    mutationFn: (input: MediaUploadInput) => mediaApi.upload(input),
    onSuccess: invalidate,
  })
}

export function useActivateMediaMutation() {
  const invalidate = useInvalidateMedia()
  return useMutation({
    mutationFn: (id: number) => mediaApi.activate(id),
    onSuccess: invalidate,
  })
}

export function useDeactivateMediaMutation() {
  const invalidate = useInvalidateMedia()
  return useMutation({
    mutationFn: (id: number) => mediaApi.deactivate(id),
    onSuccess: invalidate,
  })
}

export function useReorderBannersMutation() {
  const invalidate = useInvalidateMedia()
  return useMutation({
    mutationFn: (input: MediaReorderInput) => mediaApi.reorderBanners(input),
    onSuccess: invalidate,
  })
}
