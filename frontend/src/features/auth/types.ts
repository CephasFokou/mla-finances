import type { CurrentUser } from '../../store/authStore'

export type { CurrentUser }

export interface AuthResponse {
  token: string
  expiresAt: string
  user: CurrentUser
}

export interface LoginInput {
  email: string
  password: string
}

export interface ActivateAccountInput {
  token: string
  password: string
}

export interface ForgotPasswordInput {
  email: string
}

export interface ResetPasswordInput {
  token: string
  password: string
}
