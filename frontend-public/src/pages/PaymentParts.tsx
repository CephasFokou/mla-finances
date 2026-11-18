import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { IccLogo } from '../components/IccLogo'
import type { PublicPaymentChannel } from '../features/public/types'
import styles from './DonationDialog.module.scss'

// Pièces communes aux deux parcours de paiement du site (don à une collecte, contribution
// mensuelle) : même choix du canal, même attente « Validez sur votre téléphone », mêmes écrans de
// réussite et d'échec.

/** Pastilles des opérateurs Mobile Money ; les autres canaux gardent une pastille neutre. */
const OPERATOR_BADGES: Record<string, { label: string; background: string; color: string }> = {
  ORANGE_MONEY: { label: 'OM', background: '#FF7900', color: '#FFFFFF' },
  MTN_MOMO: { label: 'MoMo', background: '#FFCB05', color: '#23122F' },
}

export function ChannelPicker({
  channels,
  channelId,
  onSelect,
  phone,
  onPhoneChange,
  note,
}: {
  channels: PublicPaymentChannel[] | undefined
  channelId: number | null
  onSelect: (id: number) => void
  phone: string
  onPhoneChange: (value: string) => void
  note: string
}) {
  const { t } = useTranslation()
  const channel = channels?.find((c) => c.id === channelId)
  return (
    <>
      <div className={styles.label} id="channel-label">
        {t('public.dialog.payWith')}
      </div>
      <div className={styles.stack} role="radiogroup" aria-labelledby="channel-label">
        {channels?.map((c) => {
          const badge = OPERATOR_BADGES[c.code]
          return (
            <button
              key={c.id}
              type="button"
              role="radio"
              aria-checked={channelId === c.id}
              className={styles.choice}
              onClick={() => onSelect(c.id)}
            >
              <span
                className={styles.operator}
                style={badge ? { background: badge.background, color: badge.color } : undefined}
              >
                {badge?.label ?? (
                  <svg viewBox="0 0 24 24" className={styles.walletIcon} aria-hidden="true">
                    <path d="M4 7h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7zm0 0V6a2 2 0 0 1 2-2h10M16 13h1" />
                  </svg>
                )}
              </span>
              <span className={styles.choiceTitle}>{c.label}</span>
              <span className={styles.radio} />
            </button>
          )
        })}
      </div>
      {channel?.online && (
        <label className={styles.field}>
          {t('public.dialog.phone', { channel: channel.label })}
          <input
            type="tel"
            inputMode="tel"
            autoComplete="tel-national"
            value={phone}
            placeholder={t('public.dialog.phonePlaceholder')}
            onChange={(e) => onPhoneChange(e.target.value)}
          />
          <span className={styles.hint}>{t('public.dialog.phoneHint')}</span>
        </label>
      )}
      <p className={styles.note}>{note}</p>
    </>
  )
}

export function WaitingScreen({
  text,
  longWait,
  reference,
  onClose,
}: {
  text: string
  longWait: boolean
  reference: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  return (
    <div className={styles.waiting} aria-live="polite">
      <span className={styles.spinner} aria-hidden="true" />
      <h2 id="donation-title" className={styles.successTitle}>
        {t('public.dialog.waitingTitle')}
      </h2>
      <p className={styles.successText}>{text}</p>
      <p className={styles.successText}>
        {longWait ? t('public.dialog.waitingLong') : t('public.dialog.waitingHint')}
      </p>
      <ReferenceTag reference={reference} />
      <div className={styles.successActions}>
        <button type="button" className={styles.ctaGhost} onClick={onClose}>
          {t('public.dialog.close')}
        </button>
      </div>
    </div>
  )
}

export function FailureScreen({
  message,
  reference,
  onRetry,
  onClose,
}: {
  message: string | null
  reference: string
  onRetry: () => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  return (
    <div className={styles.failure} role="alert">
      <span className={styles.failureIcon} aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="M12 8v5M12 16.5v.5M4.5 19h15L12 5z" />
        </svg>
      </span>
      <h2 id="donation-title" className={styles.failureTitle}>
        {t('public.dialog.failedTitle')}
      </h2>
      <p className={styles.failureText}>{message ?? t('public.dialog.failedText')}</p>
      <ReferenceTag reference={reference} />
      <div className={styles.successActions}>
        <button type="button" className={styles.ctaGold} onClick={onRetry}>
          {t('public.dialog.retry')}
        </button>
        <button type="button" className={styles.ctaGhost} onClick={onClose}>
          {t('public.dialog.done')}
        </button>
      </div>
    </div>
  )
}

/** « L'éclosion » : le logo se redéploie autour de la confirmation. */
export function SuccessScreen({
  title,
  children,
  reference,
  actions,
}: {
  title: string
  children: ReactNode
  reference: string
  actions: ReactNode
}) {
  return (
    <div className={styles.success}>
      <span className={styles.ring} aria-hidden="true" />
      <IccLogo animated letterColor="#FFFFFF" className={styles.successLogo} />
      <h2 id="donation-title" className={styles.successTitle}>
        {title}
      </h2>
      {children}
      <ReferenceTag reference={reference} />
      <div className={styles.successActions}>{actions}</div>
    </div>
  )
}

function ReferenceTag({ reference }: { reference: string }) {
  const { t } = useTranslation()
  return (
    <div className={styles.reference}>
      {t('public.dialog.reference')} <strong>{reference}</strong>
    </div>
  )
}

/** En-tête des parcours : retour, « Étape n sur N », fermer, barre d'avancement et titre. */
export function FlowHeader({
  step,
  steps,
  stepLabel,
  title,
  onBack,
  onClose,
}: {
  step: number
  steps: number
  stepLabel: string
  title: string
  onBack: () => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  return (
    <header className={styles.head}>
      <div className={styles.headRow}>
        <button
          type="button"
          className={styles.iconBtn}
          aria-label={t('public.dialog.back')}
          onClick={onBack}
          disabled={step === 1}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M15 5l-7 7 7 7" />
          </svg>
        </button>
        <span className={styles.stepLabel}>{stepLabel}</span>
        <button
          type="button"
          className={styles.iconBtn}
          aria-label={t('public.dialog.close')}
          onClick={onClose}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>
      <div
        className={styles.segments}
        style={{ gridTemplateColumns: `repeat(${steps}, minmax(0, 1fr))` }}
        aria-hidden="true"
      >
        {Array.from({ length: steps }, (_, i) => i + 1).map((n) => (
          <span key={n} className={n <= step ? styles.segOn : undefined} />
        ))}
      </div>
      <h2 id="donation-title" className={styles.title}>
        {title}
      </h2>
    </header>
  )
}
