import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router-dom'

import { useAuthStore } from '../store/authStore'

/**
 * RG-18 : protège tout l'arbre de routes back-office (monté au-dessus d'AppLayout dans
 * router.tsx). Distingue "jamais connecté" (-> /login) de "jeton rejeté par le backend" (voir
 * expireSession dans apiClient/authStore) -> /session-expired, pour un message plus clair qu'un
 * simple renvoi silencieux vers l'écran de connexion.
 */
export function RequireAuth() {
  const token = useAuthStore((state) => state.token)
  const sessionExpired = useAuthStore((state) => state.sessionExpired)

  if (!token) {
    return <Navigate to={sessionExpired ? '/session-expired' : '/login'} replace />
  }
  return <Outlet />
}

/**
 * Garde-fou côté client, en complément (jamais en remplacement) du @PreAuthorize systématique
 * côté backend, seul rempart réel (RG-18 : permissions toujours vérifiées côté serveur). N'est
 * posé ici que sur les sections dont l'accès à la simple LECTURE doit déjà être restreint
 * (Administration → gestion des utilisateurs) ; les autres modules restent visibles en lecture à
 * tout utilisateur connecté, leurs mutations étant de toute façon bloquées côté backend si la
 * permission manque.
 */
export function RequirePermission({ permission, children }: { permission: string; children: ReactNode }) {
  const permissions = useAuthStore((state) => state.user?.permissions ?? [])
  if (!permissions.includes(permission)) {
    return <Navigate to="/unauthorized" replace />
  }
  return <>{children}</>
}
