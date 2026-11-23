import { useTranslation } from 'react-i18next'

import ui from '../../components/ui/ui.module.scss'
import { useMembersQuery } from '../../features/ledger/hooks'
import type {
  ProjectReminderAudience,
  ProjectReminders,
  ProjectRemindersInput,
} from '../../features/projects/types'
import styles from './ProjectRemindersFields.module.scss'

/** État du bloc « Relances » dans un formulaire (dates vides = valeurs par défaut du serveur). */
export interface RemindersFormState {
  enabled: boolean
  audience: ProjectReminderAudience
  openFrom: string
  dueDate: string
  message: string
  memberIds: number[]
}

export const EMPTY_REMINDERS: RemindersFormState = {
  enabled: false,
  audience: 'ALL',
  openFrom: '',
  dueDate: '',
  message: '',
  memberIds: [],
}

export function remindersState(r: ProjectReminders | undefined): RemindersFormState {
  if (!r) return EMPTY_REMINDERS
  return {
    enabled: r.enabled,
    audience: r.audience,
    openFrom: r.openFrom ?? '',
    dueDate: r.dueDate ?? '',
    message: r.message ?? '',
    memberIds: r.memberIds,
  }
}

export function remindersInput(s: RemindersFormState): ProjectRemindersInput {
  return {
    enabled: s.enabled,
    audience: s.audience,
    openFrom: s.openFrom || undefined,
    dueDate: s.dueDate || undefined,
    message: s.message.trim() || undefined,
    memberIds: s.audience === 'SELECTION' ? s.memberIds : [],
  }
}

const AUDIENCES: ProjectReminderAudience[] = ['ALL', 'RESPONSABLES', 'MEMBRES', 'SELECTION']

// Bloc « Relances » d'une collecte (cadrage Relances §6 bis) : désactivé par défaut ; cible, ouverture, échéance et
// message court repris dans la relance. Aucun montant : la participation à une collecte est libre.
export function ProjectRemindersFields({
  value,
  onChange,
  projectEndDate,
}: {
  value: RemindersFormState
  onChange: (next: RemindersFormState) => void
  projectEndDate: string
}) {
  const { t } = useTranslation()
  const members = useMembersQuery()
  const activeMembers = (members.data ?? [])
    .filter((m) => m.memberRole !== null && m.memberStatus === 'ACTIVE')
    .sort((a, b) => a.displayName.localeCompare(b.displayName, 'fr'))
  const set = (patch: Partial<RemindersFormState>) => onChange({ ...value, ...patch })
  const toggleMember = (id: number, checked: boolean) =>
    set({ memberIds: checked ? [...value.memberIds, id] : value.memberIds.filter((m) => m !== id) })

  return (
    <fieldset className={styles.block}>
      <legend>{t('projects.reminders.title')}</legend>
      <label className={styles.toggle}>
        <input type="checkbox" checked={value.enabled} onChange={(e) => set({ enabled: e.target.checked })} />
        <span>
          {t('projects.reminders.enabled')}
          <span className={ui.hint}>{t('projects.reminders.enabledHint')}</span>
        </span>
      </label>

      {value.enabled && (
        <div className={ui.formGrid}>
          <label className={`${ui.field} ${ui.span2}`}>
            {t('projects.reminders.audience')}
            <select
              value={value.audience}
              onChange={(e) => set({ audience: e.target.value as ProjectReminderAudience })}
            >
              {AUDIENCES.map((a) => (
                <option key={a} value={a}>
                  {t(`projects.reminders.audiences.${a}`)}
                </option>
              ))}
            </select>
          </label>

          {value.audience === 'SELECTION' && (
            <div className={`${ui.span2} ${styles.members}`}>
              <div className={styles.membersHead}>
                {t('projects.reminders.selected', { count: value.memberIds.length })}
              </div>
              <div className={styles.memberList}>
                {activeMembers.map((m) => (
                  <label key={m.id} className={styles.member}>
                    <input
                      type="checkbox"
                      checked={value.memberIds.includes(m.id)}
                      onChange={(e) => toggleMember(m.id, e.target.checked)}
                    />
                    <span>{m.displayName}</span>
                  </label>
                ))}
              </div>
            </div>
          )}

          <label className={ui.field}>
            {t('projects.reminders.openFrom')}
            <input type="date" value={value.openFrom} onChange={(e) => set({ openFrom: e.target.value })} />
            <span className={ui.hint}>{t('projects.reminders.openFromHint')}</span>
          </label>
          <label className={ui.field}>
            {t('projects.reminders.dueDate')}
            <input type="date" value={value.dueDate} onChange={(e) => set({ dueDate: e.target.value })} />
            <span className={ui.hint}>
              {projectEndDate ? t('projects.reminders.dueDateHint') : t('projects.reminders.dueDateRequired')}
            </span>
          </label>
          <label className={`${ui.field} ${ui.span2}`}>
            {t('projects.reminders.message')}
            <input
              type="text"
              maxLength={120}
              placeholder={t('projects.reminders.messagePlaceholder')}
              value={value.message}
              onChange={(e) => set({ message: e.target.value })}
            />
            <span className={ui.hint}>
              {t('projects.reminders.messageHint', { count: value.message.length })}
            </span>
          </label>
        </div>
      )}
    </fieldset>
  )
}
