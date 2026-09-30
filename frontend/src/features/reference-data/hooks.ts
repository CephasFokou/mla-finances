import { useQuery } from '@tanstack/react-query'

import { referenceDataApi } from './api'

// Données de référence : peu volumineuses et rarement modifiées (projets, catégories, canaux de
// paiement) — un staleTime plus long que le défaut évite de les refetcher à chaque navigation.
const REFERENCE_STALE_TIME = 5 * 60_000

export function useProjectsQuery() {
  return useQuery({
    queryKey: ['reference', 'projects'],
    queryFn: referenceDataApi.projects,
    staleTime: REFERENCE_STALE_TIME,
  })
}

export function useCategoriesQuery() {
  return useQuery({
    queryKey: ['reference', 'categories'],
    queryFn: referenceDataApi.categories,
    staleTime: REFERENCE_STALE_TIME,
  })
}

export function usePaymentChannelsQuery() {
  return useQuery({
    queryKey: ['reference', 'payment-channels'],
    queryFn: referenceDataApi.paymentChannels,
    staleTime: REFERENCE_STALE_TIME,
  })
}

export function useDonorSearchQuery(q: string) {
  return useQuery({
    queryKey: ['reference', 'donors', q],
    queryFn: () => referenceDataApi.searchDonors(q),
    staleTime: 30_000,
  })
}
