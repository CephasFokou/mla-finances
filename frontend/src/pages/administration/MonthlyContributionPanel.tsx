import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import ledger from '../../components/ledger/ledger.module.scss'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import {
  useMonthlyContributionSettingsQuery,
  useUpdateMonthlyContributionSettingsMutation,
} from '../../features/administration/hooks'
import type { MonthlyContributionSettings } from '../../features/administration/types'
import { ApiError } from '../../lib/apiClient'

/** Parcours « Contribution mensuelle » du site public : activation et montants suggérés par rôle. */
export function MonthlyContributionPanel() {
  const { t } = useTranslation()
  const { data, isLoading, isError } = useMonthlyContributionSettingsQuery()

  if (isLoading) return <div className={ui.skel} style={{ height: 220 }} />
  if (isError || !data) {
    return (
      <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
        <Icon name="alert" size={16} />
        <div>{t('monthlyContributionSettings.error')}</div>
      </div>
    )
  }
  // Clé sur la dernière mise à jour : le formulaire repart des valeurs enregistrées.
  return <SettingsForm key={data.updatedAt ?? 'initial'} settings={data} />
}

function SettingsForm({ settings }: { settings: MonthlyContributionSettings }) {
  const { t } = useTranslation()
  const mutation = useUpdateMonthlyContributionSettingsMutation()
  const [enabled, setEnabled] = useState(settings.enabled)
  const [responsable, setResponsable] = useState(String(settings.responsableAmount))
  const [member, setMember] = useState(String(settings.memberAmount))
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  const isAmount = (v: string) => /^\d+$/.test(v) && Number(v) > 0
  const valid = isAmount(responsable) && isAmount(member)

  const save = async () => {
    setMessage(null)
    if (!valid) return setMessage({ ok: false, text: t('monthlyContributionSettings.invalidAmount') })
    try {
      await mutation.mutateAsync({
        enabled,
        responsableAmount: Number(responsable),
        memberAmount: Number(member),
      })
      setMessage({ ok: true, text: t('monthlyContributionSettings.saved') })
    } catch (e) {
      setMessage({
        ok: false,
        text: e instanceof ApiError ? e.message : t('monthlyContributionSettings.genericError'),
      })
    }
  }

  return (
    <div>
      <p className={ledger.note} style={{ marginTop: 0 }}>
        {t('monthlyContributionSettings.intro')}
      </p>
      <div className={ui.card} style={{ display: 'grid', gap: 'var(--space-4)', maxWidth: 560 }}>
        <label style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            style={{ marginTop: 4 }}
          />
          <span>
            <span className={ui.cellMain}>{t('monthlyContributionSettings.enabled')}</span>
            <span className={ui.cellSub} style={{ display: 'block' }}>
              {t('monthlyContributionSettings.enabledHint')}
            </span>
          </span>
        </label>
        <label className={ui.field}>
          {t('monthlyContributionSettings.responsableAmount')}
          <input
            inputMode="numeric"
            value={responsable}
            onChange={(e) => setResponsable(e.target.value.replace(/\D/g, ''))}
          />
        </label>
        <label className={ui.field}>
          {t('monthlyContributionSettings.memberAmount')}
          <input
            inputMode="numeric"
            value={member}
            onChange={(e) => setMember(e.target.value.replace(/\D/g, ''))}
          />
        </label>
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
            {t('monthlyContributionSettings.save')}
          </button>
        </div>
      </div>
    </div>
  )
}
