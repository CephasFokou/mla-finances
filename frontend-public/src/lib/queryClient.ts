import { QueryClient } from '@tanstack/react-query'

// Client TanStack Query pour ce site (une seule page, un seul écran de requêtes) — mêmes valeurs
// par défaut que le back-office (voir frontend/src/lib/queryClient.ts) pour rester cohérent.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})
