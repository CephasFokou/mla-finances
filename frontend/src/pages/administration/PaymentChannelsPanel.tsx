import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import ledger from '../../components/ledger/ledger.module.scss'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import {
  useAllPaymentChannelsQuery,
  useSavePaymentChannelMutation,
  useTogglePaymentChannelMutation,
} from '../../features/ledger/hooks'
import { ApiError } from '../../lib/apiClient'

/** RG-15 : canaux de paiement dynamiques. Un canal désactivé disparaît des formulaires de saisie. */
export function PaymentChannelsPanel() {
  const { t } = useTranslation()
  const { data: channels, isLoading } = useAllPaymentChannelsQuery()
  const saveMutation = useSavePaymentChannelMutation()
  const toggleMutation = useTogglePaymentChannelMutation()
  const [label, setLabel] = useState('')
  const [code, setCode] = useState('')
  const [editing, setEditing] = useState<{ id: number; label: string } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const run = async (action: () => Promise<unknown>) => {
    setError(null)
    try {
      await action()
      return true
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('paymentChannels.genericError'))
      return false
    }
  }

  return (
    <div>
      <p className={ledger.note} style={{ marginTop: 0 }}>
        {t('paymentChannels.intro')}
      </p>
      <div className={ui.card} style={{ marginBottom: 'var(--space-4)' }}>
        <div className={ledger.inlineForm} style={{ padding: 0 }}>
          <label className={ui.field}>
            {t('paymentChannels.label')}
            <input value={label} placeholder="Wave" onChange={(e) => setLabel(e.target.value)} />
          </label>
          <label className={ui.field}>
            {t('paymentChannels.code')}
            <input
              value={code}
              placeholder="WAVE"
              onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_'))}
            />
          </label>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnPrimary}`}
            disabled={!label.trim() || !code.trim() || saveMutation.isPending}
            onClick={() =>
              void run(() => saveMutation.mutateAsync({ input: { label, code } })).then((ok) => {
                if (ok) {
                  setLabel('')
                  setCode('')
                }
              })
            }
          >
            <Icon name="plus" size={15} />
            {t('paymentChannels.add')}
          </button>
        </div>
      </div>
      {error && (
        <div
          className={`${ui.banner} ${ui.bannerRed}`}
          role="alert"
          style={{ marginBottom: 'var(--space-4)' }}
        >
          <Icon name="alert" size={16} />
          <div>{error}</div>
        </div>
      )}
      <div className={`${ui.card} ${ui.cardFlush}`}>
        {isLoading && <div className={ui.skel} style={{ height: 160, margin: 'var(--space-4)' }} />}
        {!!channels?.length && (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <thead>
                <tr>
                  <th>{t('paymentChannels.label')}</th>
                  <th>{t('paymentChannels.code')}</th>
                  <th>{t('paymentChannels.state')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {channels.map((c) => (
                  <tr key={c.id} className={c.active ? undefined : ledger.muted}>
                    <td>
                      {editing?.id === c.id ? (
                        <input
                          value={editing.label}
                          aria-label={t('paymentChannels.label')}
                          onChange={(e) => setEditing({ id: c.id, label: e.target.value })}
                        />
                      ) : (
                        <span className={ui.cellMain}>{c.label}</span>
                      )}
                    </td>
                    <td className={ui.cellSub}>{c.code}</td>
                    <td>
                      <span className={`${ui.pill} ${c.active ? ui.pillGreen : ui.pillGrey}`}>
                        {t(c.active ? 'paymentChannels.active' : 'paymentChannels.inactive')}
                      </span>
                    </td>
                    <td className={ledger.rowActions}>
                      {editing?.id === c.id ? (
                        <button
                          type="button"
                          className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
                          disabled={!editing.label.trim()}
                          onClick={() =>
                            void run(() =>
                              saveMutation.mutateAsync({ id: c.id, input: { label: editing.label } }),
                            ).then((ok) => ok && setEditing(null))
                          }
                        >
                          {t('paymentChannels.save')}
                        </button>
                      ) : (
                        <button
                          type="button"
                          className={ui.iconBtn}
                          aria-label={t('paymentChannels.edit', { name: c.label })}
                          onClick={() => setEditing({ id: c.id, label: c.label })}
                        >
                          <Icon name="edit" size={15} />
                        </button>
                      )}
                      <button
                        type="button"
                        className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                        disabled={toggleMutation.isPending}
                        onClick={() =>
                          void run(() => toggleMutation.mutateAsync({ id: c.id, active: !c.active }))
                        }
                      >
                        {t(c.active ? 'paymentChannels.deactivate' : 'paymentChannels.activate')}
                      </button>
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
