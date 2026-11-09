import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import ledger from '../../components/ledger/ledger.module.scss'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useActivitiesQuery, useSaveActivityMutation } from '../../features/ledger/hooks'
import type { Activity, ActivityInput, ActivityStatus } from '../../features/ledger/types'
import { ApiError } from '../../lib/apiClient'
import { formatDate } from '../../lib/format'
import { useHasPermission } from '../../store/authStore'

const EMPTY: ActivityInput = { name: '', status: 'PLANNED' }

/**
 * Activités payées par la caisse du MLA (ComFrat, rencontres, matériel...). Une dépense de la
 * caisse s'y rattache pour suivre son coût net (Rapports → Activités). Ce n'est pas une collecte :
 * l'argent reste dans la caisse.
 */
export function ActivitiesPanel() {
  const { t } = useTranslation()
  const { data: activities, isLoading } = useActivitiesQuery()
  const saveMutation = useSaveActivityMutation()
  const canEdit = useHasPermission('MANAGE_PROJECTS')
  const [editing, setEditing] = useState<{ id?: number; input: ActivityInput } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const set = (patch: Partial<ActivityInput>) =>
    setEditing((current) => (current ? { ...current, input: { ...current.input, ...patch } } : current))

  const start = (activity?: Activity) => {
    setError(null)
    setEditing(
      activity
        ? {
            id: activity.id,
            input: {
              name: activity.name,
              periodLabel: activity.periodLabel ?? '',
              eventDate: activity.eventDate ?? '',
              status: activity.status,
              comment: activity.comment ?? '',
            },
          }
        : { input: { ...EMPTY } },
    )
  }

  const save = async () => {
    if (!editing) return
    setError(null)
    try {
      await saveMutation.mutateAsync({
        id: editing.id,
        input: {
          ...editing.input,
          eventDate: editing.input.eventDate || undefined,
          periodLabel: editing.input.periodLabel || undefined,
          comment: editing.input.comment || undefined,
        },
      })
      setEditing(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('activities.genericError'))
    }
  }

  return (
    <div>
      <p className={ledger.note} style={{ marginTop: 0 }}>
        {t('activities.intro')}
      </p>
      {canEdit && !editing && (
        <div className={ui.actions} style={{ marginBottom: 'var(--space-4)' }}>
          <button type="button" className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`} onClick={() => start()}>
            <Icon name="plus" size={14} />
            {t('activities.new')}
          </button>
        </div>
      )}
      {editing && (
        <div className={ui.card} style={{ marginBottom: 'var(--space-4)' }}>
          <div className={ledger.inlineForm} style={{ padding: 0 }}>
            <label className={ui.field}>
              {t('activities.name')}
              <input value={editing.input.name} onChange={(e) => set({ name: e.target.value })} />
            </label>
            <label className={ui.field}>
              {t('activities.date')}
              <input
                type="date"
                value={editing.input.eventDate ?? ''}
                onChange={(e) => set({ eventDate: e.target.value })}
              />
            </label>
            <label className={ui.field}>
              {t('activities.periodLabel')}
              <input
                value={editing.input.periodLabel ?? ''}
                placeholder={t('activities.periodLabelPlaceholder')}
                onChange={(e) => set({ periodLabel: e.target.value })}
              />
            </label>
            <label className={ui.field}>
              {t('activities.status')}
              <select
                value={editing.input.status}
                onChange={(e) => set({ status: e.target.value as ActivityStatus })}
              >
                <option value="PLANNED">{t('activityStatus.PLANNED')}</option>
                <option value="COMPLETED">{t('activityStatus.COMPLETED')}</option>
              </select>
            </label>
            <label className={`${ui.field} ${ui.span2}`}>
              {t('activities.comment')}
              <input value={editing.input.comment ?? ''} onChange={(e) => set({ comment: e.target.value })} />
            </label>
          </div>
          {error && <div className={`${ui.banner} ${ui.bannerRed}`}>{error}</div>}
          <div className={ui.actions} style={{ marginTop: 'var(--space-3)' }}>
            <button
              type="button"
              className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
              onClick={() => setEditing(null)}
            >
              {t('activities.cancel')}
            </button>
            <button
              type="button"
              className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
              disabled={!editing.input.name.trim() || saveMutation.isPending}
              onClick={() => void save()}
            >
              {t('activities.save')}
            </button>
          </div>
        </div>
      )}
      <div className={`${ui.card} ${ui.cardFlush}`}>
        {isLoading && <div className={ui.skel} style={{ height: 160, margin: 'var(--space-4)' }} />}
        {!!activities?.length && (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <thead>
                <tr>
                  <th>{t('activities.name')}</th>
                  <th>{t('activities.date')}</th>
                  <th>{t('activities.status')}</th>
                  <th>{t('activities.comment')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {activities.map((a) => (
                  <tr key={a.id}>
                    <td className={ui.cellMain}>{a.name}</td>
                    <td>{a.periodLabel || (a.eventDate ? formatDate(a.eventDate) : '–')}</td>
                    <td>
                      <span className={`${ui.pill} ${a.status === 'COMPLETED' ? ui.pillGrey : ui.pillAmber}`}>
                        {t(`activityStatus.${a.status}`)}
                      </span>
                    </td>
                    <td className={ui.cellSub}>{a.comment ?? ''}</td>
                    <td className={ledger.rowActions}>
                      {canEdit && (
                        <button
                          type="button"
                          className={ui.iconBtn}
                          aria-label={t('activities.edit', { name: a.name })}
                          onClick={() => start(a)}
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
    </div>
  )
}
