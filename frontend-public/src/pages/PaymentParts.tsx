import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { IccLogo } from '../components/IccLogo'
import type { PaymentChoice } from '../features/public/payment'
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

/** Pastille d'un canal (OM, MoMo, ou icône portefeuille pour les canaux manuels). */
function ChannelBadge({ channel }: { channel: PublicPaymentChannel }) {
  const badge = OPERATOR_BADGES[channel.code]
  return (
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
  )
}

/**
 * Étape « Paiement », identique dans les deux parcours : un seul champ numéro, l'opérateur est
 * reconnu à partir du préfixe (pas de choix Orange/MTN à faire), et un lien « Payer autrement »
 * pour les canaux manuels. Toute la logique est dans usePaymentChoice.
 */
export function PaymentStep({
  choice,
  onlineNote,
  manualNote,
}: {
  choice: PaymentChoice
  onlineNote: string
  manualNote: string
}) {
  const { t } = useTranslation()
  const operatorName = (code: string) => t(`public.payment.operators.${code}`)

  if (choice.manualMode) {
    return (
      <>
        <div className={styles.label} id="channel-label">
          {t('public.payment.otherTitle')}
        </div>
        <div className={styles.stack} role="radiogroup" aria-labelledby="channel-label">
          {choice.manualChannels.map((c) => (
            <button
              key={c.id}
              type="button"
              role="radio"
              aria-checked={choice.manualId === c.id}
              className={styles.choice}
              onClick={() => choice.setManualId(c.id)}
            >
              <ChannelBadge channel={c} />
              <span className={styles.choiceTitle}>{c.label}</span>
              <span className={styles.radio} />
            </button>
          ))}
        </div>
        <label className={styles.field}>
          {t('public.payment.contactPhone')}
          <input
            type="tel"
            inputMode="tel"
            autoComplete="tel-national"
            value={choice.phone}
            placeholder={t('public.dialog.phonePlaceholder')}
            onChange={(e) => choice.setPhone(e.target.value)}
          />
          <span className={styles.hint}>{t('public.payment.contactHint')}</span>
        </label>
        <p className={styles.note}>{manualNote}</p>
        {choice.canSwitch && (
          <button type="button" className={styles.linkBtn} onClick={() => choice.setOtherWay(false)}>
            {t('public.payment.backToMobile')}
          </button>
        )}
      </>
    )
  }

  return (
    <>
      <label className={styles.field}>
        {t('public.payment.phone')}
        <input
          type="tel"
          inputMode="tel"
          autoComplete="tel-national"
          value={choice.phone}
          placeholder={t('public.dialog.phonePlaceholder')}
          onChange={(e) => choice.setPhone(e.target.value)}
        />
        {!choice.valid && <span className={styles.hint}>{t('public.payment.phoneHint')}</span>}
      </label>

      {choice.detectedChannel && (
        <p className={styles.detected} aria-live="polite">
          <ChannelBadge channel={choice.detectedChannel} />
          <span>{t('public.payment.detected', { operator: choice.detectedChannel.label })}</span>
        </p>
      )}
      {choice.issue === 'unavailable' && choice.detected && choice.detected !== 'UNSUPPORTED' && (
        <p className={styles.warn} role="alert">
          {t('public.payment.unavailable', { operator: operatorName(choice.detected) })}
        </p>
      )}
      {choice.issue === 'unsupported' && (
        <p className={styles.warn} role="alert">
          {t('public.payment.unsupported')}
        </p>
      )}
      {choice.issue === 'pickOperator' && (
        <>
          <div className={styles.label} id="operator-label">
            {t('public.payment.pickOperator')}
          </div>
          <div className={styles.stack} role="radiogroup" aria-labelledby="operator-label">
            {choice.onlineChannels.map((c) => (
              <button
                key={c.id}
                type="button"
                role="radio"
                aria-checked={choice.pickedOnlineId === c.id}
                className={styles.choice}
                onClick={() => choice.setPickedOnlineId(c.id)}
              >
                <ChannelBadge channel={c} />
                <span className={styles.choiceTitle}>{c.label}</span>
                <span className={styles.radio} />
              </button>
            ))}
          </div>
        </>
      )}

      <p className={styles.note}>{onlineNote}</p>
      {choice.canSwitch && (
        <button type="button" className={styles.linkBtn} onClick={() => choice.setOtherWay(true)}>
          {t('public.payment.otherWay')}
        </button>
      )}
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
