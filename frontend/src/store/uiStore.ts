import { create } from 'zustand'

interface UiState {
  sidebarCollapsed: boolean
  toggleSidebar: () => void
}

/**
 * Store Zustand pour l'état d'interface transverse (ex. repli de la barre latérale du
 * back-office). Sert de gabarit pour les futurs stores métier (session, filtres partagés, etc.) —
 * un store par domaine, jamais un store global fourre-tout.
 */
export const useUiStore = create<UiState>((set) => ({
  sidebarCollapsed: false,
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
}))
