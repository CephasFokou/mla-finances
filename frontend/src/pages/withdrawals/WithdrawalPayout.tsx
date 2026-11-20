import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import type {
  MobileMoneyBalance,
  PayoutRecipient,
  PayoutStatus,
  WithdrawalResponse,
} from '../../features/withdrawals/types'
import { formatDateTime, formatFcfa } from '../../lib/format'
import styles from './WithdrawalDetailPage.module.scss'

const PAYOUT_VARIANTS: Record<PayoutStatus, string | undefined> = {
  PROCESSING: ui.pillAmber,
  PENDING_APPROVAL: ui.pillNavy,
  COMPLETED: ui.pillGreen,
  FAILED: ui.pillRed,
}

export function PayoutStatusPill({ status }: { status: PayoutStatus }) {
  const { t } = useTranslation()
  return (
    <span className={`${ui.pill} ${PAYOUT_VARIANTS[status]}`}>
      {t(`withdrawals.payout.status.${status}`)}
    </span>
  )
}

/** Motif d'échec en clair : libellé connu, sinon le code tel quel. */
function useFailureLabel() {
  const { t } = useTranslation()
  return (code: string) => t(`withdrawals.payout.failure.${code}`, { defaultValue: code })
}

const HINTS: Record<PayoutStatus, { key: string; banner: string | undefined }> = {
  PROCESSING: { key: 'processingHint', banner: ui.bannerAmber },
  PENDING_APPROVAL: { key: 'pendingApprovalHint', banner: ui.bannerNavy },
  COMPLETED: { key: 'completedHint', banner: ui.bannerGreen },
  FAILED: { key: 'failedHint', banner: ui.bannerRed },
}

/** Bloc « Décaissement Mobile Money » de la fiche retrait. */
export function WithdrawalPayoutCard({
  withdrawal,
  canRecheck,
  isRechecking,
  onRecheck,
}: {
  withdrawal: WithdrawalResponse
  canRecheck: boolean
  isRechecking: boolean
  onRecheck: () => void
}) {
  const { t } = useTranslation()
  const failureLabel = useFailureLabel()
  const status = withdrawal.payoutStatus
  if (!status) return null
  const hint = HINTS[status]
  const isActive = status === 'PROCESSING' || status === 'PENDING_APPROVAL'

  return (
    <div className={ui.card}>
      <div className={ui.cardHead}>
        <h3>{t('withdrawals.payout.title')}</h3>
        <PayoutStatusPill status={status} />
      </div>

      <div className={`${ui.banner} ${hint.banner} ${styles.spaced}`} role="status">
        <Icon name={status === 'FAILED' ? 'alert' : status === 'COMPLETED' ? 'check' : 'clock'} size={17} />
        <div>
          {status === 'FAILED' && withdrawal.payoutFailureCode && (
            <strong>{failureLabel(withdrawal.payoutFailureCode)}. </strong>
          )}
          {t(`withdrawals.payout.${hint.key}`)}
        </div>
      </div>

      <table className={styles.infoTable}>
        <tbody>
          <tr>
            <th scope="row">{t('withdrawals.payout.operator')}</th>
            <td>
              {withdrawal.payoutProvider
                ? t(`withdrawals.payout.provider.${withdrawal.payoutProvider}`)
                : '—'}
            </td>
          </tr>
          <tr>
            <th scope="row">{t('withdrawals.payout.phone')}</th>
            <td className={styles.amount}>{withdrawal.payoutPhoneMasked ?? '—'}</td>
          </tr>
          {withdrawal.payoutRequestedAt && (
            <tr>
              <th scope="row">{t('withdrawals.payout.requestedAt')}</th>
              <td>
                {formatDateTime(withdrawal.payoutRequestedAt)}
                {withdrawal.payoutRequestedByName && (
                  <span className={ui.cellSub}> · {withdrawal.payoutRequestedByName}</span>
                )}
              </td>
            </tr>
          )}
          {withdrawal.payoutCompletedAt && (
            <tr>
              <th scope="row">{t('withdrawals.payout.completedAt')}</th>
              <td>{formatDateTime(withdrawal.payoutCompletedAt)}</td>
            </tr>
          )}
          {withdrawal.payoutProviderRef && (
            <tr>
              <th scope="row">{t('withdrawals.payout.providerRef')}</th>
              <td className={styles.amount}>{withdrawal.payoutProviderRef}</td>
            </tr>
          )}
          {status === 'FAILED' && withdrawal.payoutFailureCode && (
            <tr>
              <th scope="row">{t('withdrawals.payout.failureLabel')}</th>
              <td>{failureLabel(withdrawal.payoutFailureCode)}</td>
            </tr>
          )}
          {withdrawal.payoutId && (
            <tr>
              <th scope="row">{t('withdrawals.payout.payoutId')}</th>
              <td className={ui.cellSub}>{withdrawal.payoutId}</td>
            </tr>
          )}
        </tbody>
      </table>

      {isActive && canRecheck && (
        <button
          type="button"
          className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm} ${styles.recheck}`}
          disabled={isRechecking}
          onClick={onRecheck}
        >
          <Icon name="repeat" size={14} />
          {t('withdrawals.payout.recheck')}
        </button>
      )}
    </div>
  )
}

/** Choix du numéro (liste fermée) puis confirmation explicite du montant et du destinataire. */
export function PayoutDialog({
  withdrawal,
  recipients,
  isSending,
  error,
  onCancel,
  onConfirm,
}: {
  withdrawal: WithdrawalResponse
  recipients: PayoutRecipient[]
  isSending: boolean
  error: string | null
  onCancel: () => void
  onConfirm: (recipientKey: string) => void
}) {
  const { t } = useTranslation()
  const [recipientKey, setRecipientKey] = useState(recipients[0]?.key ?? '')
  const recipient = recipients.find((r) => r.key === recipientKey)

  return (
    <div className={styles.overlay} onClick={onCancel}>
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="payout-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.modalHead}>
          <span className={`${styles.modalIcon} ${styles.iconNavy}`}>
            <Icon name="out" size={18} />
          </span>
          <h3 id="payout-title">{t('withdrawals.payout.dialogTitle')}</h3>
        </div>
        <div className={styles.modalBody}>
          {recipients.length === 0 ? (
            <p>{t('withdrawals.payout.noRecipient')}</p>
          ) : (
            <fieldset className={styles.recipients}>
              <legend>{t('withdrawals.payout.recipientLabel')}</legend>
              {recipients.map((r) => (
                <label
                  key={r.key}
                  className={`${styles.recipient} ${r.key === recipientKey ? styles.recipientSelected : ''}`}
                >
                  <input
                    type="radio"
                    name="payout-recipient"
                    value={r.key}
                    checked={r.key === recipientKey}
                    onChange={() => setRecipientKey(r.key)}
                  />
                  <span>
                    <span className={ui.cellMain}>{t(`withdrawals.payout.provider.${r.provider}`)}</span>
                    <span className={ui.cellSub}>
                      {' '}
                      · {r.maskedPhone} · {r.label}
                    </span>
                  </span>
                </label>
              ))}
              <span className={ui.sub}>{t('withdrawals.payout.recipientHint')}</span>
            </fieldset>
          )}
          {recipient && (
            <p className={styles.confirmLine}>
              {t('withdrawals.payout.confirm', {
                amount: formatFcfa(withdrawal.amount),
                operator: t(`withdrawals.payout.provider.${recipient.provider}`),
                phone: recipient.maskedPhone,
              })}
            </p>
          )}
          <p className={ui.sub}>{t('withdrawals.payout.confirmHint')}</p>
          {error && <span className={ui.error}>{error}</span>}
        </div>
        <div className={styles.modalFoot}>
          <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={onCancel}>
            {t('withdrawals.form.cancel')}
          </button>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnPrimary}`}
            disabled={!recipient || isSending}
            onClick={() => recipient && onConfirm(recipient.key)}
          >
            {t('withdrawals.payout.send')}
          </button>
        </div>
      </div>
    </div>
  )
}

/** Contrôle d'écart entre le solde de la Trésorerie et ce que MLA Finance attend. */
function MobileMoneyGap({ balance }: { balance: MobileMoneyBalance }) {
  const { t } = useTranslation()
  if (balance.gap === null || balance.expected === null) {
    return <p className={`${ui.sub} ${styles.gapLine}`}>{t('withdrawals.payout.balance.gapUnavailable')}</p>
  }
  const ok = balance.gap === 0
  return (
    <div className={`${ui.banner} ${ok ? ui.bannerGreen : ui.bannerAmber} ${styles.gapLine}`} role="status">
      <Icon name={ok ? 'check' : 'alert'} size={17} />
      <div>
        <strong>{t('withdrawals.payout.balance.gapTitle')}. </strong>
        {ok
          ? t('withdrawals.payout.balance.noGap')
          : t('withdrawals.payout.balance.gap', { amount: formatFcfa(balance.gap) })}{' '}
        {t('withdrawals.payout.balance.expected', { amount: formatFcfa(balance.expected) })}
        {balance.checkSince && (
          <> · {t('withdrawals.payout.balance.since', { date: formatDateTime(balance.checkSince) })}</>
        )}
        {!ok && <p className={styles.gapExplain}>{t('withdrawals.payout.balance.gapExplain')}</p>}
      </div>
    </div>
  )
}

/** Encart « Solde Mobile Money du MLA » (lecture seule) de la page Retraits. */
export function MobileMoneyBalanceCard({ balance }: { balance: MobileMoneyBalance }) {
  const { t } = useTranslation()
  if (!balance.enabled) return null
  return (
    <div className={`${ui.card} ${styles.spaced}`}>
      <div className={ui.cardHead}>
        <h3>{t('withdrawals.payout.balance.title')}</h3>
        {balance.asOf && (
          <span className={ui.sub}>
            {t('withdrawals.payout.balance.asOf', { date: formatDateTime(balance.asOf) })}
          </span>
        )}
      </div>
      {balance.reachable && balance.available !== null ? (
        <>
          <div className={styles.mmBalance}>
            <div>
              <div className={ui.kpiLabel}>{t('withdrawals.payout.balance.available')}</div>
              <div className={ui.kpiValue}>{formatFcfa(balance.available)}</div>
            </div>
            <div>
              <div className={ui.kpiLabel}>{t('withdrawals.payout.balance.reserved')}</div>
              <div className={ui.kpiValue}>{formatFcfa(balance.reserved ?? 0)}</div>
            </div>
          </div>
          <p className={ui.sub}>{t('withdrawals.payout.balance.hint')}</p>
          <MobileMoneyGap balance={balance} />
        </>
      ) : (
        <div className={`${ui.banner} ${ui.bannerAmber}`} role="status">
          <Icon name="alert" size={17} />
          <div>{t('withdrawals.payout.balance.unreachable')}</div>
        </div>
      )}
    </div>
  )
}
