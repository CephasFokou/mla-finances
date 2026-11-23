import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useAuthStore } from '../../store/authStore'
import { useUpdateProjectMutation } from '../../features/projects/hooks'
import type { Project } from '../../features/projects/types'
import { ApiError } from '../../lib/apiClient'
import { formatDate } from '../../lib/format'
import styles from './ProjectDetailPage.module.scss'
import { ProjectRemindersFields, remindersInput, remindersState } from './ProjectRemindersFields'

// Carte « Relances » de la fiche d'une collecte (cadrage Relances §6 bis) : état du bloc, modification, et lien vers
// l'écran Relances sur ce motif quand la collecte est relançable.
export function ProjectRemindersCard({ project, canEdit }: { project: Project; canEdit: boolean }) {
  const { t } = useTranslation()
  const updateMutation = useUpdateProjectMutation(project.id)
  const canSendReminders = useAuthStore((st) => st.user?.permissions.includes('SEND_REMINDERS') ?? false)
  const [editing, setEditing] = useState(false)
  const [state, setState] = useState(() => remindersState(project.reminders))
  const [error, setError] = useState<string | null>(null)
  const r = project.reminders

  const start = () => {
    setState(remindersState(project.reminders))
    setError(null)
    setEditing(true)
  }

  const save = async () => {
    setError(null)
    try {
      await updateMutation.mutateAsync({
        name: project.name,
        description: project.description ?? undefined,
        targetAmount: project.targetAmount ?? undefined,
        startDate: project.startDate ?? undefined,
        endDate: project.endDate ?? undefined,
        reminders: remindersInput(state),
      })
      setEditing(false)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('projects.form.genericError'))
    }
  }

  return (
    <div className={ui.card}>
      <div className={ui.cardHead}>
        <h3>{t('projects.reminders.title')}</h3>
        {!editing && canEdit && (
          <button type="button" className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`} onClick={start}>
            <Icon name="edit" />
            {t('projects.detail.edit')}
          </button>
        )}
      </div>

      {!editing && !r.enabled && <p className={ui.sub}>{t('projects.reminders.off')}</p>}
      {!editing && r.enabled && (
        <table className={styles.infoTable}>
          <tbody>
            <tr>
              <th scope="row">{t('projects.reminders.audience')}</th>
              <td>
                {t(`projects.reminders.audiences.${r.audience}`)}
                {r.audience === 'SELECTION' && ` (${r.memberIds.length})`}
              </td>
            </tr>
            <tr>
              <th scope="row">{t('projects.reminders.period')}</th>
              <td>
                {r.openFrom ? formatDate(r.openFrom) : '—'} — {r.dueDate ? formatDate(r.dueDate) : '—'}
              </td>
            </tr>
            <tr>
              <th scope="row">{t('projects.reminders.message')}</th>
              <td>{r.message || '—'}</td>
            </tr>
          </tbody>
        </table>
      )}
      {!editing && r.enabled && project.status === 'ACTIVE' && canSendReminders && (
        <Link to={`/reminders?projectId=${project.id}`} className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}>
          <Icon name="repeat" strokeWidth={2.4} />
          {t('projects.reminders.open')}
        </Link>
      )}
      {!editing && r.enabled && project.status === 'DRAFT' && (
        <p className={ui.hint}>{t('projects.reminders.draftHint')}</p>
      )}

      {editing && (
        <div className={styles.editBlock}>
          <ProjectRemindersFields value={state} onChange={setState} projectEndDate={project.endDate ?? ''} />
          {error && <span className={ui.error}>{error}</span>}
          <div className={`${ui.actions} ${styles.right}`}>
            <button
              type="button"
              className={`${ui.btn} ${ui.btnSecondary}`}
              onClick={() => setEditing(false)}
            >
              {t('projects.detail.cancelEdit')}
            </button>
            <button
              type="button"
              className={`${ui.btn} ${ui.btnPrimary}`}
              disabled={updateMutation.isPending}
              onClick={() => void save()}
            >
              {t('projects.detail.saveEdit')}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
