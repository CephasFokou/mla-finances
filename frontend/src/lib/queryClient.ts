import { QueryClient } from '@tanstack/react-query'

// Client TanStack Query partagé par toute l'application. Les valeurs par défaut ci-dessous
// évitent des refetchs agressifs sur les écrans financiers (tableaux de bord, listes) tout en
// gardant les données raisonnablement fraîches ; à ajuster module par module si besoin.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})
