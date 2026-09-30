import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { administrationApi } from './api'
import type { RolePermissionsUpdateInput, UserInviteInput, UserRolesUpdateInput } from './types'

export function useUsersQuery() {
  return useQuery({ queryKey: ['administration', 'users'], queryFn: administrationApi.listUsers })
}

export function useRolesQuery() {
  return useQuery({ queryKey: ['administration', 'roles'], queryFn: administrationApi.listRoles })
}

export function useInviteUserMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: UserInviteInput) => administrationApi.inviteUser(input),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['administration', 'users'] }),
  })
}

export function useUpdateUserRolesMutation(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: UserRolesUpdateInput) => administrationApi.updateUserRoles(id, input),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['administration', 'users'] }),
  })
}

export function useResendInvitationMutation(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => administrationApi.resendInvitation(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['administration', 'users'] }),
  })
}

export function useSuspendUserMutation(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => administrationApi.suspendUser(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['administration', 'users'] }),
  })
}

export function useReactivateUserMutation(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => administrationApi.reactivateUser(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['administration', 'users'] }),
  })
}

export function useResetUserPasswordMutation(id: number) {
  return useMutation({ mutationFn: () => administrationApi.resetUserPassword(id) })
}

export function useUpdateRolePermissionsMutation(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: RolePermissionsUpdateInput) => administrationApi.updateRolePermissions(id, input),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['administration', 'roles'] }),
  })
}
