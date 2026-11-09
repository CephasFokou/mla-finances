import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import ledger from '../../components/ledger/ledger.module.scss'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useMembersQuery, useSaveMemberMutation } from '../../features/ledger/hooks'
import type { Member, MemberInput, MemberRole, MemberStatus } from '../../features/ledger/types'
import { ApiError } from '../../lib/apiClient'
import { useHasPermission } from '../../store/authStore'

type RoleFilter = 'all' | MemberRole | 'THIRD_PARTY'

const EMPTY: MemberInput = { displayName: '', memberRole: 'MEMBRE', memberStatus: 'ACTIVE' }

function toInput(member: Member): MemberInput {
  return {
    displayName: member.displayName,
    firstName: member.firstName ?? '',
    lastName: member.lastName ?? '',
    memberRole: member.memberRole,
    memberStatus: member.memberStatus,
    email: member.email ?? '',
    phone: member.phone ?? '',
    remark: member.remark ?? '',
  }
}

/**
 * Membres du MLA (responsables et membres, onglet Membres du fichier de suivi) et tiers
 * (donateurs externes). Le nom usuel est celui qu'on retrouve dans le journal des mouvements.
 */
export function MembersPage() {
  const { t } = useTranslation()
  const { data: members, isLoading, isError } = useMembersQuery()
  const saveMutation = useSaveMemberMutation()
  const canEdit = useHasPermission('MANAGE_CONTRIBUTIONS')
  const [filter, setFilter] = useState<RoleFilter>('all')
  const [editing, setEditing] = useState<{ id?: number; input: MemberInput } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const visible = useMemo(
    () =>
      (members ?? []).filter((m) =>
        filter === 'all' ? true : filter === 'THIRD_PARTY' ? m.memberRole === null : m.memberRole === filter,
      ),
    [members, filter],
  )
  const counts = useMemo(() => {
    const list = members ?? []
    return {
      RESPONSABLE: list.filter((m) => m.memberRole === 'RESPONSABLE' && m.memberStatus === 'ACTIVE').length,
      MEMBRE: list.filter((m) => m.memberRole === 'MEMBRE' && m.memberStatus === 'ACTIVE').length,
      THIRD_PARTY: list.filter((m) => m.memberRole === null).length,
    }
  }, [members])

  const set = (patch: Partial<MemberInput>) =>
    setEditing((current) => (current ? { ...current, input: { ...current.input, ...patch } } : current))

  const save = async () => {
    if (!editing) return
    setError(null)
    try {
      await saveMutation.mutateAsync(editing)
      setEditing(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('members.genericError'))
    }
  }

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('members.title')}</h1>
          <p>
            {t('members.subtitle', {
              responsables: counts.RESPONSABLE,
              members: counts.MEMBRE,
              thirdParties: counts.THIRD_PARTY,
            })}
          </p>
        </div>
        {canEdit && (
          <button
            type="button"
            className={`${ui.btn} ${ui.btnPrimary}`}
            onClick={() => {
              setError(null)
              setEditing({ input: { ...EMPTY } })
            }}
          >
            <Icon name="plus" size={15} />
            {t('members.new')}
          </button>
        )}
      </header>

      {editing && (
        <div className={ui.card} style={{ marginBottom: 'var(--space-5)' }}>
          <h3 style={{ marginTop: 0 }}>{editing.id ? t('members.editTitle') : t('members.newTitle')}</h3>
          <div className={ui.formGrid}>
            <label className={ui.field}>
              {t('members.fields.displayName')}
              <input
                value={editing.input.displayName}
                onChange={(e) => set({ displayName: e.target.value })}
              />
              <span className={ui.hint}>{t('members.fields.displayNameHint')}</span>
            </label>
            <label className={ui.field}>
              {t('members.fields.role')}
              <select
                value={editing.input.memberRole ?? 'THIRD_PARTY'}
                onChange={(e) =>
                  set({
                    memberRole: e.target.value === 'THIRD_PARTY' ? null : (e.target.value as MemberRole),
                    memberStatus:
                      e.target.value === 'THIRD_PARTY' ? null : (editing.input.memberStatus ?? 'ACTIVE'),
                  })
                }
              >
                <option value="RESPONSABLE">{t('memberRole.RESPONSABLE')}</option>
                <option value="MEMBRE">{t('memberRole.MEMBRE')}</option>
                <option value="THIRD_PARTY">{t('memberRole.THIRD_PARTY')}</option>
              </select>
            </label>
            <label className={ui.field}>
              {t('members.fields.firstName')}
              <input
                value={editing.input.firstName ?? ''}
                onChange={(e) => set({ firstName: e.target.value })}
              />
            </label>
            <label className={ui.field}>
              {t('members.fields.lastName')}
              <input
                value={editing.input.lastName ?? ''}
                onChange={(e) => set({ lastName: e.target.value })}
              />
            </label>
            {editing.input.memberRole && (
              <label className={ui.field}>
                {t('members.fields.status')}
                <select
                  value={editing.input.memberStatus ?? 'ACTIVE'}
                  onChange={(e) => set({ memberStatus: e.target.value as MemberStatus })}
                >
                  {(['ACTIVE', 'LEFT', 'NOT_INTEGRATED'] as const).map((s) => (
                    <option key={s} value={s}>
                      {t(`memberStatus.${s}`)}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <label className={ui.field}>
              {t('members.fields.phone')}
              <input value={editing.input.phone ?? ''} onChange={(e) => set({ phone: e.target.value })} />
            </label>
            <label className={ui.field}>
              {t('members.fields.email')}
              <input
                type="email"
                value={editing.input.email ?? ''}
                onChange={(e) => set({ email: e.target.value })}
              />
            </label>
            <label className={`${ui.field} ${ui.span2}`}>
              {t('members.fields.remark')}
              <input value={editing.input.remark ?? ''} onChange={(e) => set({ remark: e.target.value })} />
            </label>
          </div>
          {error && (
            <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
              <Icon name="alert" size={16} />
              <div>{error}</div>
            </div>
          )}
          <div className={ui.actions}>
            <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={() => setEditing(null)}>
              {t('members.cancel')}
            </button>
            <button
              type="button"
              className={`${ui.btn} ${ui.btnPrimary}`}
              disabled={!editing.input.displayName.trim() || saveMutation.isPending}
              onClick={() => void save()}
            >
              {t('members.save')}
            </button>
          </div>
        </div>
      )}

      <div className={ui.seg} role="tablist" aria-label={t('members.filter')}>
        {(['all', 'RESPONSABLE', 'MEMBRE', 'THIRD_PARTY'] as const).map((key) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={filter === key}
            className={filter === key ? ui.segActive : undefined}
            onClick={() => setFilter(key)}
          >
            {key === 'all' ? t('members.all') : t(`memberRole.plural.${key}`)}
          </button>
        ))}
      </div>

      <div className={`${ui.card} ${ui.cardFlush}`} style={{ marginTop: 'var(--space-4)' }}>
        {isLoading && <div className={ui.skel} style={{ height: 240, margin: 'var(--space-4)' }} />}
        {isError && <div className={`${ui.banner} ${ui.bannerRed}`}>{t('members.error')}</div>}
        {!!visible.length && (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <thead>
                <tr>
                  <th>{t('members.fields.displayName')}</th>
                  <th>{t('members.fields.fullName')}</th>
                  <th>{t('members.fields.role')}</th>
                  <th>{t('members.fields.status')}</th>
                  <th>{t('members.fields.remark')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {visible.map((m) => (
                  <tr
                    key={m.id}
                    className={m.memberStatus && m.memberStatus !== 'ACTIVE' ? ledger.muted : undefined}
                  >
                    <td className={ui.cellMain}>{m.displayName}</td>
                    <td>{m.fullName && m.fullName !== m.displayName ? m.fullName : '–'}</td>
                    <td>
                      <span
                        className={`${ui.pill} ${m.memberRole === 'RESPONSABLE' ? ui.pillNavy : ui.pillGrey}`}
                      >
                        {t(`memberRole.${m.memberRole ?? 'THIRD_PARTY'}`)}
                      </span>
                    </td>
                    <td>{m.memberStatus ? t(`memberStatus.${m.memberStatus}`) : '–'}</td>
                    <td className={ui.cellSub}>{m.remark ?? ''}</td>
                    <td className={ledger.rowActions}>
                      {canEdit && (
                        <button
                          type="button"
                          className={ui.iconBtn}
                          aria-label={t('members.edit', { name: m.displayName })}
                          onClick={() => {
                            setError(null)
                            setEditing({ id: m.id, input: toInput(m) })
                          }}
                        >
                          <Icon name="edit" size={15} />
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
    </section>
  )
}
