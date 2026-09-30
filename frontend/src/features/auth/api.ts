import { apiClient } from '../../lib/apiClient'
import type {
  ActivateAccountInput,
  AuthResponse,
  ForgotPasswordInput,
  LoginInput,
  ResetPasswordInput,
} from './types'

export const authApi = {
  login: (input: LoginInput) => apiClient.post<AuthResponse>('/auth/login', input),
  activate: (input: ActivateAccountInput) => apiClient.post<AuthResponse>('/auth/activate', input),
  forgotPassword: (input: ForgotPasswordInput) => apiClient.post<void>('/auth/forgot-password', input),
  resetPassword: (input: ResetPasswordInput) => apiClient.post<AuthResponse>('/auth/reset-password', input),
}
