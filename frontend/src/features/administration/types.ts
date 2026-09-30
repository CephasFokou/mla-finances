// Types calqués sur le contrat JSON du module Administration (UserController/RoleController).

export type UserAccountStatus = 'PENDING_INVITATION' | 'ACTIVE' | 'SUSPENDED'

export type Permission =
  | 'VIEW_DASHBOARD'
  | 'MANAGE_CONTRIBUTIONS'
  | 'MANAGE_PROJECTS'
  | 'MANAGE_WITHDRAWALS'
  | 'VALIDATE_WITHDRAWALS'
  | 'MANAGE_USERS'
  | 'MANAGE_MEDIA'
  | 'VIEW_AUDIT_LOG'

export interface RoleRef {
  id: number
  name: string
}

export interface RoleResponse {
  id: number
  name: string
  description: string | null
  permissions: Permission[]
}

export interface UserResponse {
  id: number
  email: string
  fullName: string
  status: UserAccountStatus
  roles: RoleRef[]
  invitationToken: string | null
  invitationExpiresAt: string | null
  lastLoginAt: string | null
  createdAt: string
}

export interface UserInviteInput {
  email: string
  fullName: string
  roleIds: number[]
}

export interface UserRolesUpdateInput {
  roleIds: number[]
}

export interface RolePermissionsUpdateInput {
  permissions: Permission[]
}
