// Client HTTP minimal pour les seuls endpoints publics (/api/v1/public/**, tous permitAll côté
// SecurityConfig) — pas de jeton d'authentification à gérer ici, contrairement au client du
// back-office (voir frontend/src/lib/apiClient.ts) : cette page ne fait jamais partie de
// l'application protégée.

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
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })

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

  return (await response.json()) as T
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body !== undefined ? JSON.stringify(body) : undefined }),
}
