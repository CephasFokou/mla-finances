import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import {
  useReactivateUserMutation,
  useResendInvitationMutation,
  useResetUserPasswordMutation,
  useRolesQuery,
  useSuspendUserMutation,
  useUpdateUserRolesMutation,
  useUsersQuery,
} from '../../features/administration/hooks'
import type { UserResponse } from '../../features/administration/types'
import { ApiError } from '../../lib/apiClient'
import { formatDateTime } from '../../lib/format'
import { UserStatusPill } from './UserStatusPill'
import styles from './AdministrationPage.module.scss'

type PendingAction = { type: 'suspend' | 'reactivate'; user: UserResponse }

function InvitationHint({ user }: { user: UserResponse }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  if (user.status !== 'PENDING_INVITATION' || !user.invitationToken) return null

  const copy = () => {
    void navigator.clipboard.writeText(user.invitationToken as string).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }

  return (
    <div className={styles.invitationHint}>
      <span>{t('administration.users.invitationTokenLabel')}</span>
      <span className={styles.invitationToken}>{user.invitationToken.slice(0, 8)}…</span>
      <button type="button" className={styles.copyBtn} onClick={copy}>
        {copied ? t('administration.users.copied') : t('administration.users.copy')}
      </button>
    </div>
  )
}

function EditRolesModal({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const { t } = useTranslation()
  const { data: roles } = useRolesQuery()
  const [selected, setSelected] = useState<number[]>(user.roles.map((r) => r.id))
  const [error, setError] = useState<string | null>(null)
  const mutation = useUpdateUserRolesMutation(user.id)

  const toggle = (roleId: number, checked: boolean) => {
    setSelected((prev) => (checked ? [...prev, roleId] : prev.filter((id) => id !== roleId)))
  }

  const save = async () => {
    setError(null)
    try {
      await mutation.mutateAsync({ roleIds: selected })
      onClose()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('administration.users.genericError'))
    }
  }

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="edit-roles-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.modalHead}>
          <span className={`${styles.modalIcon} ${styles.iconNavy}`}>
            <Icon name="gear" size={18} />
          </span>
          <h3 id="edit-roles-title">{t('administration.users.editRolesTitle', { name: user.fullName })}</h3>
        </div>
        <div className={styles.modalBody}>
          <div className={styles.roleCheckList}>
            {roles?.map((role) => (
              <label key={role.id} className={styles.roleCheckRow}>
                <input
                  type="checkbox"
                  checked={selected.includes(role.id)}
                  onChange={(e) => toggle(role.id, e.target.checked)}
                />
                {role.name}
              </label>
            ))}
          </div>
          {error && <span className={ui.error}>{error}</span>}
        </div>
        <div className={styles.modalFoot}>
          <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={onClose}>
            {t('administration.users.cancel')}
          </button>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnPrimary}`}
            disabled={selected.length === 0 || mutation.isPending}
            onClick={() => void save()}
          >
            {t('administration.users.save')}
          </button>
        </div>
      </div>
    </div>
  )
}

export function UsersPanel() {
  const { t } = useTranslation()
  const { data: users, isLoading, isError } = useUsersQuery()
  const [editingUser, setEditingUser] = useState<UserResponse | null>(null)
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const suspendMutation = useSuspendUserMutation(pendingAction?.user.id ?? -1)
  const reactivateMutation = useReactivateUserMutation(pendingAction?.user.id ?? -1)

  const runPendingAction = async () => {
    if (!pendingAction) return
    setActionError(null)
    try {
      if (pendingAction.type === 'suspend') await suspendMutation.mutateAsync()
      else await reactivateMutation.mutateAsync()
      setPendingAction(null)
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : t('administration.users.genericError'))
    }
  }

  return (
    <div>
      <div className={`${ui.actions} ${styles.panelActions}`}>
        <Link to="/administration/users/new" className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}>
          <Icon name="plus" strokeWidth={2.4} />
          {t('administration.users.new')}
        </Link>
      </div>

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
            <h4>{t('administration.users.error')}</h4>
          </div>
        )}
        {!isLoading && !isError && users?.length === 0 && (
          <div className={ui.emptyState}>
            <div className={ui.emptyIcon}>
              <Icon name="folder" size={24} />
            </div>
            <h4>{t('administration.users.empty')}</h4>
          </div>
        )}
        {!isLoading && !isError && !!users?.length && (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <thead>
                <tr>
                  <th>{t('administration.users.columns.name')}</th>
                  <th>{t('administration.users.columns.email')}</th>
                  <th>{t('administration.users.columns.roles')}</th>
                  <th>{t('administration.users.columns.status')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id}>
                    <td className={ui.cellMain}>{user.fullName}</td>
                    <td className={ui.cellSub}>{user.email}</td>
                    <td>
                      <div className={styles.rolesCell}>
                        {user.roles.map((role) => (
                          <span key={role.id} className={`${ui.pill} ${ui.pillGrey}`}>
                            {role.name}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td>
                      <UserStatusPill status={user.status} />
                      <InvitationHint user={user} />
                      {user.status === 'ACTIVE' && user.lastLoginAt && (
                        <div className={ui.cellSub}>{formatDateTime(user.lastLoginAt)}</div>
                      )}
                    </td>
                    <td className={styles.rowActions}>
                      <button
                        type="button"
                        className={ui.iconBtn}
                        aria-label={t('administration.users.editRoles')}
                        title={t('administration.users.editRoles')}
                        onClick={() => setEditingUser(user)}
                      >
                        <Icon name="edit" size={14} />
                      </button>
                      {user.status === 'PENDING_INVITATION' && <ResendInvitationButton userId={user.id} />}
                      {user.status === 'ACTIVE' && <ResetPasswordButton userId={user.id} />}
                      {(user.status === 'ACTIVE' || user.status === 'PENDING_INVITATION') && (
                        <button
                          type="button"
                          className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                          onClick={() => setPendingAction({ type: 'suspend', user })}
                        >
                          {t('administration.users.suspend')}
                        </button>
                      )}
                      {user.status === 'SUSPENDED' && (
                        <button
                          type="button"
                          className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                          onClick={() => setPendingAction({ type: 'reactivate', user })}
                        >
                          {t('administration.users.reactivate')}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {editingUser && <EditRolesModal user={editingUser} onClose={() => setEditingUser(null)} />}

      {pendingAction && (
        <div className={styles.overlay} onClick={() => setPendingAction(null)}>
          <div
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="user-action-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className={styles.modalHead}>
              <span
                className={`${styles.modalIcon} ${pendingAction.type === 'suspend' ? styles.iconRed : styles.iconNavy}`}
              >
                <Icon name={pendingAction.type === 'suspend' ? 'alert' : 'check'} size={18} />
              </span>
              <h3 id="user-action-title">{t(`administration.users.${pendingAction.type}`)}</h3>
            </div>
            <div className={styles.modalBody}>
              <p>
                {t(`administration.users.${pendingAction.type}Confirm`, {
                  name: pendingAction.user.fullName,
                })}
              </p>
              {actionError && <span className={ui.error}>{actionError}</span>}
            </div>
            <div className={styles.modalFoot}>
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary}`}
                onClick={() => setPendingAction(null)}
              >
                {t('administration.users.cancel')}
              </button>
              <button
                type="button"
                className={
                  pendingAction.type === 'suspend'
                    ? `${ui.btn} ${ui.btnDanger}`
                    : `${ui.btn} ${ui.btnPrimary}`
                }
                onClick={() => void runPendingAction()}
              >
                {t(`administration.users.${pendingAction.type}`)}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function ResendInvitationButton({ userId }: { userId: number }) {
  const { t } = useTranslation()
  const mutation = useResendInvitationMutation(userId)
  return (
    <button
      type="button"
      className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
      disabled={mutation.isPending}
      onClick={() => void mutation.mutateAsync()}
    >
      {t('administration.users.resendInvitation')}
    </button>
  )
}

function ResetPasswordButton({ userId }: { userId: number }) {
  const { t } = useTranslation()
  const mutation = useResetUserPasswordMutation(userId)
  return (
    <button
      type="button"
      className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
      disabled={mutation.isPending}
      onClick={() => void mutation.mutateAsync()}
    >
      {mutation.isSuccess
        ? t('administration.users.resetPasswordSent')
        : t('administration.users.resetPassword')}
    </button>
  )
}
