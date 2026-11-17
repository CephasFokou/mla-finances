import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { IccLogo } from '../components/IccLogo'
import {
  useDonationStatusQuery,
  usePublicPaymentChannelsQuery,
  useSubmitPublicDonationMutation,
} from '../features/public/hooks'
import type { PublicCollection, PublicDonationStatus } from '../features/public/types'
import { ApiError } from '../lib/apiClient'
import { formatFcfa } from '../lib/format'
import styles from './DonationDialog.module.scss'

const AMOUNT_PRESETS = [1000, 2000, 5000, 10000]
/** Numéro où envoyer le paiement, affiché après l'enregistrement s'il est configuré au déploiement. */
const PAYMENT_NUMBER: string | undefined = import.meta.env.VITE_MLA_PAYMENT_NUMBER

/** Pastilles des opérateurs Mobile Money ; les autres canaux gardent une pastille neutre. */
const OPERATOR_BADGES: Record<string, { label: string; background: string; color: string }> = {
  ORANGE_MONEY: { label: 'OM', background: '#FF7900', color: '#FFFFFF' },
  MTN_MOMO: { label: 'MoMo', background: '#FFCB05', color: '#23122F' },
}

interface DonationDialogProps {
  onClose: () => void
  collections: PublicCollection[]
  ribbonColor: (id: number) => string
  initialCollectionId: number | null
}

/** Au-delà de ce délai d'attente de la validation Mobile Money, on explique au donateur qu'il peut fermer. */
const LONG_WAIT_MS = 120_000

/**
 * Parcours de don en trois étapes (collecte, montant, paiement), puis « l'éclosion » : le logo se
 * redéploie autour de la confirmation. Deux modes selon le canal choisi : en ligne (Orange Money /
 * MTN MoMo via PawaPay — une demande de paiement est poussée sur le téléphone, la page suit le
 * statut jusqu'à la confirmation ou l'échec) ou manuel (le donateur paie de son côté, le
 * secrétariat confirme à réception des fonds).
 */
/** Monté seulement quand il est ouvert : chaque ouverture repart d'un parcours vierge. */
export function DonationDialog({
  onClose,
  collections,
  ribbonColor,
  initialCollectionId,
}: DonationDialogProps) {
  const { t } = useTranslation()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const { data: channels } = usePublicPaymentChannelsQuery()
  const mutation = useSubmitPublicDonationMutation()

  const [step, setStep] = useState(initialCollectionId ? 2 : 1)
  const [collectionId, setCollectionId] = useState<number | null>(initialCollectionId)
  const [amount, setAmount] = useState<number | null>(null)
  const [custom, setCustom] = useState('')
  const [anonymous, setAnonymous] = useState(false)
  const [name, setName] = useState('')
  const [contact, setContact] = useState('')
  const [channelId, setChannelId] = useState<number | null>(null)
  const [phone, setPhone] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [longWait, setLongWait] = useState(false)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
    return () => dialog?.close()
  }, [])

  const value = custom ? Number(custom) : (amount ?? 0)
  const collection = collections.find((c) => c.id === collectionId)
  const channel = channels?.find((c) => c.id === channelId)
  const result = mutation.data
  const online = !!channel?.online
  const phoneDigits = phone.replace(/\D/g, '')
  const phoneLooksValid =
    phoneDigits.length === 9 || (phoneDigits.length === 12 && phoneDigits.startsWith('237'))

  // Suivi d'un don en ligne tant qu'il n'est pas final (voir useDonationStatusQuery).
  const tracking =
    result && result.mode === 'ONLINE' && result.status === 'PROCESSING' ? result.trackingToken : null
  const statusQuery = useDonationStatusQuery(tracking)
  const liveStatus: PublicDonationStatus | undefined = statusQuery.data?.status ?? result?.status
  const failureMessage = statusQuery.data?.failureMessage ?? result?.failureMessage ?? null

  useEffect(() => {
    if (!tracking) return
    const timer = window.setTimeout(() => setLongWait(true), LONG_WAIT_MS)
    return () => {
      window.clearTimeout(timer)
      setLongWait(false)
    }
  }, [tracking])

  const canContinue =
    (step === 1 && !!collection) ||
    (step === 2 && value > 0 && (anonymous || name.trim().length > 0)) ||
    (step === 3 && !!channel && (!online || phoneLooksValid) && !mutation.isPending)

  const reset = () => {
    mutation.reset()
    setStep(1)
    setAmount(null)
    setCustom('')
    setChannelId(null)
    setPhone('')
    setError(null)
  }

  /** Après un échec Mobile Money : on garde la collecte, le montant et le nom, on revient au paiement. */
  const retry = () => {
    mutation.reset()
    setError(null)
    setStep(3)
  }

  const close = () => onClose()

  const next = async () => {
    setError(null)
    if (step === 2 && !(value > 0)) return setError(t('public.dialog.amountRequired'))
    if (step === 2 && !anonymous && !name.trim()) return setError(t('public.dialog.nameRequired'))
    if (step < 3) return setStep(step + 1)
    if (!collection || !channel) return
    if (online && !phoneLooksValid) return setError(t('public.dialog.phoneInvalid'))
    try {
      await mutation.mutateAsync({
        projectId: collection.id,
        paymentChannelId: channel.id,
        amount: value,
        anonymous,
        donorName: anonymous ? undefined : name.trim(),
        donorContact: anonymous ? undefined : contact.trim() || undefined,
        payerPhone: online ? phoneDigits : undefined,
      })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('public.dialog.error'))
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby="donation-title"
      onCancel={(e) => {
        e.preventDefault()
        close()
      }}
    >
      {result && result.mode === 'ONLINE' && liveStatus === 'PROCESSING' ? (
        <div className={styles.waiting} aria-live="polite">
          <span className={styles.spinner} aria-hidden="true" />
          <h2 id="donation-title" className={styles.successTitle}>
            {t('public.dialog.waitingTitle')}
          </h2>
          <p className={styles.successText}>
            {t('public.dialog.waitingText', {
              amount: formatFcfa(value),
              phone: phoneDigits.replace(/^237/, ''),
              channel: channel?.label ?? '',
            })}
          </p>
          <p className={styles.successText}>
            {longWait ? t('public.dialog.waitingLong') : t('public.dialog.waitingHint')}
          </p>
          <div className={styles.reference}>
            {t('public.dialog.reference')} <strong>{result.reference}</strong>
          </div>
          <div className={styles.successActions}>
            <button type="button" className={styles.ctaGhost} onClick={close}>
              {t('public.dialog.close')}
            </button>
          </div>
        </div>
      ) : result && result.mode === 'ONLINE' && (liveStatus === 'FAILED' || liveStatus === 'CANCELLED') ? (
        <div className={styles.failure} role="alert">
          <span className={styles.failureIcon} aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path d="M12 8v5M12 16.5v.5M4.5 19h15L12 5z" />
            </svg>
          </span>
          <h2 id="donation-title" className={styles.failureTitle}>
            {t('public.dialog.failedTitle')}
          </h2>
          <p className={styles.failureText}>{failureMessage ?? t('public.dialog.failedText')}</p>
          <div className={styles.reference}>
            {t('public.dialog.reference')} <strong>{result.reference}</strong>
          </div>
          <div className={styles.successActions}>
            <button type="button" className={styles.ctaGold} onClick={retry}>
              {t('public.dialog.retry')}
            </button>
            <button type="button" className={styles.ctaGhost} onClick={close}>
              {t('public.dialog.done')}
            </button>
          </div>
        </div>
      ) : result ? (
        <div className={styles.success}>
          <span className={styles.ring} aria-hidden="true" />
          <IccLogo animated letterColor="#FFFFFF" className={styles.successLogo} />
          <h2 id="donation-title" className={styles.successTitle}>
            {result.mode === 'ONLINE' ? t('public.dialog.paidTitle') : t('public.dialog.successTitle')}
          </h2>
          <p className={styles.successText}>
            {result.mode === 'ONLINE'
              ? t('public.dialog.paidText', {
                  amount: formatFcfa(value),
                  collection: collection?.name ?? '',
                })
              : t('public.dialog.successText', {
                  amount: formatFcfa(value),
                  collection: collection?.name ?? '',
                  channel: channel?.label ?? '',
                })}
          </p>
          {result.mode === 'MANUAL' && PAYMENT_NUMBER && (
            <p className={styles.successText}>{t('public.dialog.payTo', { number: PAYMENT_NUMBER })}</p>
          )}
          <div className={styles.reference}>
            {t('public.dialog.reference')} <strong>{result.reference}</strong>
          </div>
          <div className={styles.successActions}>
            <button type="button" className={styles.ctaGold} onClick={reset}>
              {t('public.dialog.again')}
            </button>
            <button type="button" className={styles.ctaGhost} onClick={close}>
              {t('public.dialog.done')}
            </button>
          </div>
        </div>
      ) : (
        <div className={styles.flow}>
          <header className={styles.head}>
            <div className={styles.headRow}>
              <button
                type="button"
                className={styles.iconBtn}
                aria-label={t('public.dialog.back')}
                onClick={() => setStep(Math.max(1, step - 1))}
                disabled={step === 1}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M15 5l-7 7 7 7" />
                </svg>
              </button>
              <span className={styles.stepLabel}>{t('public.dialog.step', { step })}</span>
              <button
                type="button"
                className={styles.iconBtn}
                aria-label={t('public.dialog.close')}
                onClick={close}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M6 6l12 12M18 6L6 18" />
                </svg>
              </button>
            </div>
            <div className={styles.segments} aria-hidden="true">
              {[1, 2, 3].map((n) => (
                <span key={n} className={n <= step ? styles.segOn : undefined} />
              ))}
            </div>
            <h2 id="donation-title" className={styles.title}>
              {t(`public.dialog.titles.${step}`)}
            </h2>
          </header>

          <div className={styles.body} key={step}>
            {step === 1 && (
              <div className={styles.stack} role="radiogroup" aria-labelledby="donation-title">
                {collections.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    role="radio"
                    aria-checked={collectionId === c.id}
                    className={styles.choice}
                    onClick={() => setCollectionId(c.id)}
                  >
                    <span className={styles.ribbonMark} style={{ background: ribbonColor(c.id) }} />
                    <span className={styles.choiceText}>
                      <span className={styles.choiceTitle}>{c.name}</span>
                      <span className={styles.choiceSub}>
                        {c.contributionsCount > 0
                          ? t('public.dialog.collectionSub', {
                              count: c.contributionsCount,
                              amount: formatFcfa(c.collected),
                            })
                          : t('public.dialog.collectionSubNone')}
                      </span>
                    </span>
                    <span className={styles.radio} />
                  </button>
                ))}
                <p className={styles.note}>{t('public.dialog.separate')}</p>
              </div>
            )}

            {step === 2 && (
              <div className={styles.stack}>
                <div>
                  <div className={styles.label} id="amount-label">
                    {t('public.dialog.amount')}
                  </div>
                  <div className={styles.chips} role="group" aria-labelledby="amount-label">
                    {AMOUNT_PRESETS.map((v) => (
                      <button
                        key={v}
                        type="button"
                        aria-pressed={!custom && amount === v}
                        className={styles.chip}
                        onClick={() => {
                          setAmount(v)
                          setCustom('')
                        }}
                      >
                        {formatFcfa(v)}
                      </button>
                    ))}
                  </div>
                </div>
                <label className={styles.field}>
                  {t('public.dialog.otherAmount')}
                  <input
                    type="number"
                    inputMode="numeric"
                    min="1"
                    value={custom}
                    placeholder={t('public.dialog.otherAmountPlaceholder')}
                    onChange={(e) => setCustom(e.target.value)}
                  />
                </label>
                <label className={styles.check}>
                  <input
                    type="checkbox"
                    checked={anonymous}
                    onChange={(e) => setAnonymous(e.target.checked)}
                  />
                  {t('public.dialog.anonymous')}
                </label>
                {!anonymous && (
                  <>
                    <label className={styles.field}>
                      {t('public.dialog.name')}
                      <input
                        value={name}
                        autoComplete="name"
                        placeholder={t('public.dialog.namePlaceholder')}
                        onChange={(e) => setName(e.target.value)}
                      />
                    </label>
                    <label className={styles.field}>
                      {t('public.dialog.contact')}
                      <input
                        value={contact}
                        autoComplete="tel"
                        onChange={(e) => setContact(e.target.value)}
                      />
                      <span className={styles.hint}>{t('public.dialog.contactHint')}</span>
                    </label>
                  </>
                )}
              </div>
            )}

            {step === 3 && (
              <div className={styles.stack}>
                <div className={styles.summary}>
                  <span>{collection?.name}</span>
                  <strong>{formatFcfa(value)}</strong>
                </div>
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
                        onClick={() => setChannelId(c.id)}
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
                {online && (
                  <label className={styles.field}>
                    {t('public.dialog.phone', { channel: channel?.label ?? '' })}
                    <input
                      type="tel"
                      inputMode="tel"
                      autoComplete="tel-national"
                      value={phone}
                      placeholder={t('public.dialog.phonePlaceholder')}
                      onChange={(e) => setPhone(e.target.value)}
                    />
                    <span className={styles.hint}>{t('public.dialog.phoneHint')}</span>
                  </label>
                )}
                <p className={styles.note}>
                  {online ? t('public.dialog.onlineNote') : t('public.dialog.manualNote')}
                </p>
              </div>
            )}

            {error && (
              <p className={styles.error} role="alert">
                {error}
              </p>
            )}
          </div>

          <footer className={styles.foot}>
            <button type="button" className={styles.cta} disabled={!canContinue} onClick={() => void next()}>
              {step < 3
                ? t('public.dialog.continue')
                : mutation.isPending
                  ? online
                    ? t('public.dialog.paying')
                    : t('public.dialog.submitting')
                  : online
                    ? t('public.dialog.pay', { amount: formatFcfa(value) })
                    : t('public.dialog.submit')}
            </button>
          </footer>
        </div>
      )}
    </dialog>
  )
}
