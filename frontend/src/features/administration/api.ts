import { apiClient } from '../../lib/apiClient'
import type {
  MonthlyContributionSettings,
  MonthlyContributionSettingsInput,
  RolePermissionsUpdateInput,
  RoleResponse,
  UserInviteInput,
  UserResponse,
  UserRolesUpdateInput,
} from './types'

export const administrationApi = {
  listUsers: () => apiClient.get<UserResponse[]>('/users'),

  getUser: (id: number) => apiClient.get<UserResponse>(`/users/${id}`),

  inviteUser: (input: UserInviteInput) => apiClient.post<UserResponse>('/users', input),

  updateUserRoles: (id: number, input: UserRolesUpdateInput) =>
    apiClient.put<UserResponse>(`/users/${id}/roles`, input),

  resendInvitation: (id: number) => apiClient.post<UserResponse>(`/users/${id}/resend-invitation`),

  suspendUser: (id: number) => apiClient.post<UserResponse>(`/users/${id}/suspend`),

  reactivateUser: (id: number) => apiClient.post<UserResponse>(`/users/${id}/reactivate`),

  resetUserPassword: (id: number) => apiClient.post<void>(`/users/${id}/reset-password`),

  getMonthlyContributionSettings: () =>
    apiClient.get<MonthlyContributionSettings>('/settings/monthly-contribution'),

  updateMonthlyContributionSettings: (input: MonthlyContributionSettingsInput) =>
    apiClient.put<MonthlyContributionSettings>('/settings/monthly-contribution', input),

  listRoles: () => apiClient.get<RoleResponse[]>('/roles'),

  updateRolePermissions: (id: number, input: RolePermissionsUpdateInput) =>
    apiClient.put<RoleResponse>(`/roles/${id}/permissions`, input),
}
