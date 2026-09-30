// Client HTTP minimal partagé par tous les modules. RG-18 : le jeton JWT de la session (voir
// store/authStore) est ajouté automatiquement ici — un seul endroit à modifier, comme prévu.

import { useAuthStore } from '../store/authStore'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080/api/v1'

export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, body: unknown, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

interface FieldError {
  field: string
  message: string
}

function messageFromErrorBody(status: number, body: unknown): string {
  if (body && typeof body === 'object') {
    // 422 métier ({ rule, message }) et 404/409 simples ({ message }) partagent ce champ.
    if ('message' in body && typeof (body as { message?: unknown }).message === 'string') {
      return (body as { message: string }).message
    }
    // 400 de validation ({ errors: [{ field, message }] }).
    if ('errors' in body && Array.isArray((body as { errors?: unknown }).errors)) {
      const fieldErrors = (body as { errors: FieldError[] }).errors
      return fieldErrors.map((e) => `${e.field} : ${e.message}`).join(' — ')
    }
  }
  return `Erreur ${status}`
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  // Un corps FormData (upload de fichier) ne doit jamais recevoir Content-Type: application/json
  // — le navigateur doit fixer lui-même le Content-Type multipart/form-data avec sa boundary.
  const isFormData = init?.body instanceof FormData
  const token = useAuthStore.getState().token
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  })

  if (response.status === 401 && token) {
    // Le jeton était présent mais rejeté (expiré/révoqué) : ce n'est pas un échec de connexion
    // ordinaire (voir LoginPage, qui gère l'ApiError 401 localement quand aucun jeton n'était
    // encore attaché) mais une session qui doit se terminer.
    useAuthStore.getState().expireSession()
  }

  if (!response.ok) {
    let body: unknown = null
    try {
      body = await response.json()
    } catch {
      // pas de corps JSON (ex. 500 brut) — on garde body = null
    }
    throw new ApiError(response.status, body, messageFromErrorBody(response.status, body))
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('text/csv')) {
    return (await response.text()) as unknown as T
  }

  return (await response.json()) as T
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body !== undefined ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body !== undefined ? JSON.stringify(body) : undefined }),
  upload: <T>(path: string, formData: FormData) => request<T>(path, { method: 'POST', body: formData }),
}

export { BASE_URL }
