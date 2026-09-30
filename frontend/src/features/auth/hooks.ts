import { useMutation } from '@tanstack/react-query'

import { authApi } from './api'
import type { ActivateAccountInput, ForgotPasswordInput, LoginInput, ResetPasswordInput } from './types'

export function useLoginMutation() {
  return useMutation({ mutationFn: (input: LoginInput) => authApi.login(input) })
}

export function useActivateAccountMutation() {
  return useMutation({ mutationFn: (input: ActivateAccountInput) => authApi.activate(input) })
}

export function useForgotPasswordMutation() {
  return useMutation({ mutationFn: (input: ForgotPasswordInput) => authApi.forgotPassword(input) })
}

export function useResetPasswordMutation() {
  return useMutation({ mutationFn: (input: ResetPasswordInput) => authApi.resetPassword(input) })
}
