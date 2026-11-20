import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import ledger from '../../components/ledger/ledger.module.scss'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useReminderSettingsQuery, useUpdateReminderSettingsMutation } from '../../features/reminders/hooks'
import type { ReminderSettings } from '../../features/reminders/types'
import { ApiError } from '../../lib/apiClient'

/** Réglages des relances : activation, ouverture dans le mois, délais d'attente, maximum, plage horaire d'envoi. */
export function ReminderSettingsPanel() {
  const { t } = useTranslation()
  const { data, isLoading, isError } = useReminderSettingsQuery()

  if (isLoading) return <div className={ui.skel} style={{ height: 260 }} />
  if (isError || !data) {
    return (
      <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
        <Icon name="alert" size={16} />
        <div>{t('reminderSettings.error')}</div>
      </div>
    )
  }
  return <SettingsForm key={data.updatedAt ?? 'initial'} settings={data} />
}

type NumberKey =
  'openFromDay' | 'cooldownNoResponseDays' | 'cooldownAckDays' | 'cooldownNotFoundHours' | 'maxPerMonth'
const NUMBER_FIELDS: { key: NumberKey; min: number; max: number }[] = [
  { key: 'openFromDay', min: 1, max: 28 },
  { key: 'cooldownNoResponseDays', min: 0, max: 31 },
  { key: 'maxPerMonth', min: 1, max: 10 },
  { key: 'cooldownAckDays', min: 0, max: 31 },
  { key: 'cooldownNotFoundHours', min: 0, max: 240 },
]

function SettingsForm({ settings }: { settings: ReminderSettings }) {
  const { t } = useTranslation()
  const mutation = useUpdateReminderSettingsMutation()
  const [enabled, setEnabled] = useState(settings.enabled)
  const [numbers, setNumbers] = useState<Record<NumberKey, string>>({
    openFromDay: String(settings.openFromDay),
    cooldownNoResponseDays: String(settings.cooldownNoResponseDays),
    cooldownAckDays: String(settings.cooldownAckDays),
    cooldownNotFoundHours: String(settings.cooldownNotFoundHours),
    maxPerMonth: String(settings.maxPerMonth),
  })
  const [start, setStart] = useState(settings.sendWindowStart.slice(0, 5))
  const [end, setEnd] = useState(settings.sendWindowEnd.slice(0, 5))
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  const valid =
    NUMBER_FIELDS.every(({ key, min, max }) => {
      const v = Number(numbers[key])
      return numbers[key] !== '' && Number.isInteger(v) && v >= min && v <= max
    }) && start < end

  const save = async () => {
    setMessage(null)
    if (!valid) return setMessage({ ok: false, text: t('reminderSettings.invalid') })
    try {
      await mutation.mutateAsync({
        enabled,
        openFromDay: Number(numbers.openFromDay),
        cooldownNoResponseDays: Number(numbers.cooldownNoResponseDays),
        cooldownAckDays: Number(numbers.cooldownAckDays),
        cooldownNotFoundHours: Number(numbers.cooldownNotFoundHours),
        maxPerMonth: Number(numbers.maxPerMonth),
        sendWindowStart: start,
        sendWindowEnd: end,
        autoReminderOnPromise: settings.autoReminderOnPromise,
      })
      setMessage({ ok: true, text: t('reminderSettings.saved') })
    } catch (e) {
      setMessage({ ok: false, text: e instanceof ApiError ? e.message : t('reminderSettings.genericError') })
    }
  }

  return (
    <div>
      <p className={ledger.note} style={{ marginTop: 0 }}>
        {t('reminderSettings.intro')}
      </p>
      <div className={ui.card} style={{ display: 'grid', gap: 'var(--space-4)', maxWidth: 620 }}>
        <label style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            style={{ marginTop: 4 }}
          />
          <span>
            <span className={ui.cellMain}>{t('reminderSettings.enabled')}</span>
            <span className={ui.cellSub} style={{ display: 'block' }}>
              {t('reminderSettings.enabledHint')}
            </span>
          </span>
        </label>
        <div className={ui.formGrid}>
          {NUMBER_FIELDS.map(({ key, min, max }) => (
            <label key={key} className={ui.field}>
              {t(`reminderSettings.fields.${key}`)}
              <input
                inputMode="numeric"
                value={numbers[key]}
                aria-describedby={`${key}-hint`}
                onChange={(e) =>
                  setNumbers((prev) => ({ ...prev, [key]: e.target.value.replace(/\D/g, '') }))
                }
              />
              <span id={`${key}-hint`} className={ui.cellSub}>
                {t(`reminderSettings.hints.${key}`, { min, max })}
              </span>
            </label>
          ))}
          <label className={ui.field}>
            {t('reminderSettings.fields.sendWindowStart')}
            <input type="time" value={start} onChange={(e) => setStart(e.target.value)} />
          </label>
          <label className={ui.field}>
            {t('reminderSettings.fields.sendWindowEnd')}
            <input type="time" value={end} onChange={(e) => setEnd(e.target.value)} />
          </label>
        </div>
        <p className={ui.cellSub} style={{ margin: 0 }}>
          {t('reminderSettings.nextLots')}
        </p>
        {message && (
          <div className={`${ui.banner} ${message.ok ? ui.bannerGreen : ui.bannerRed}`} role="status">
            <Icon name={message.ok ? 'check' : 'alert'} size={16} />
            <div>{message.text}</div>
          </div>
        )}
        <div>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnPrimary}`}
            disabled={mutation.isPending}
            onClick={() => void save()}
          >
            {t('reminderSettings.save')}
          </button>
        </div>
      </div>
    </div>
  )
}
