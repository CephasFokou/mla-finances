import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface CurrentUser {
  id: number
  email: string
  fullName: string
  roles: string[]
  permissions: string[]
}

interface AuthState {
  token: string | null
  user: CurrentUser | null
  /** Distingue "jamais connecté" de "jeton rejeté par le backend" (voir apiClient), pour rediriger
   *  vers /session-expired plutôt que /login dans ce second cas. */
  sessionExpired: boolean
  setSession: (token: string, user: CurrentUser) => void
  expireSession: () => void
  logout: () => void
}

/**
 * Store Zustand pour la session d'authentification (RG-18) : jeton JWT + utilisateur courant,
 * persisté en localStorage pour survivre à un rechargement de page. Un store dédié à ce domaine,
 * comme useUiStore pour l'état d'interface transverse — jamais de store fourre-tout.
 *
 * Pas de rafraîchissement automatique de /api/v1/auth/me au chargement : les permissions affichées
 * reflètent le dernier login jusqu'à la prochaine connexion (ou jusqu'à ce que le backend rejette le
 * jeton, auquel cas apiClient appelle expireSession()). Choix documenté dans
 * claude/module-authentification-spec.md.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      sessionExpired: false,
      setSession: (token, user) => set({ token, user, sessionExpired: false }),
      expireSession: () => set({ token: null, user: null, sessionExpired: true }),
      logout: () => set({ token: null, user: null, sessionExpired: false }),
    }),
    { name: 'mla-finance-auth' },
  ),
)
