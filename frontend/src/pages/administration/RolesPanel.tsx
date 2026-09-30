import { useTranslation } from 'react-i18next'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useRolesQuery, useUpdateRolePermissionsMutation } from '../../features/administration/hooks'
import type { Permission, RoleResponse } from '../../features/administration/types'
import styles from './AdministrationPage.module.scss'

const PERMISSIONS: Permission[] = [
  'VIEW_DASHBOARD',
  'MANAGE_CONTRIBUTIONS',
  'MANAGE_PROJECTS',
  'MANAGE_WITHDRAWALS',
  'VALIDATE_WITHDRAWALS',
  'MANAGE_USERS',
  'MANAGE_MEDIA',
  'VIEW_AUDIT_LOG',
]

function RoleRow({ role }: { role: RoleResponse }) {
  const mutation = useUpdateRolePermissionsMutation(role.id)

  const toggle = (permission: Permission, checked: boolean) => {
    const next = checked
      ? [...role.permissions, permission]
      : role.permissions.filter((p) => p !== permission)
    void mutation.mutateAsync({ permissions: next })
  }

  return (
    <tr>
      <td>
        <div className={styles.roleName}>{role.name}</div>
        {role.description && <div className={styles.roleDescription}>{role.description}</div>}
      </td>
      {PERMISSIONS.map((permission) => (
        <td key={permission}>
          <input
            type="checkbox"
            className={styles.checkbox}
            checked={role.permissions.includes(permission)}
            disabled={mutation.isPending}
            onChange={(e) => toggle(permission, e.target.checked)}
            aria-label={permission}
          />
        </td>
      ))}
    </tr>
  )
}

export function RolesPanel() {
  const { t } = useTranslation()
  const { data: roles, isLoading, isError } = useRolesQuery()

  return (
    <div className={`${ui.card} ${ui.cardFlush}`}>
      {isLoading && (
        <div className={styles.skeletons}>
          <div className={ui.skel} />
          <div className={ui.skel} />
          <div className={ui.skel} />
        </div>
      )}
      {isError && (
        <div className={ui.emptyState}>
          <div className={ui.emptyIcon}>
            <Icon name="alert" size={24} />
          </div>
          <h4>{t('administration.roles.error')}</h4>
        </div>
      )}
      {!isLoading && !isError && !!roles?.length && (
        <div className={styles.matrixWrap}>
          <table className={styles.matrix}>
            <thead>
              <tr>
                <th>{t('administration.roles.columns.role')}</th>
                {PERMISSIONS.map((permission) => (
                  <th key={permission}>{t(`administration.permissions.${permission}`)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {roles.map((role) => (
                <RoleRow key={role.id} role={role} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
