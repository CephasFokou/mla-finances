import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { IccLogo } from '../components/IccLogo'
import { usePublicPaymentChannelsQuery, useSubmitPublicDonationMutation } from '../features/public/hooks'
import type { PublicCollection } from '../features/public/types'
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

/**
 * Parcours de don en trois étapes (collecte, montant, paiement), puis « l'éclosion » : le logo se
 * redéploie autour de la confirmation. Le don crée une intention de paiement que le secrétariat
 * confirme à réception des fonds (aucun prestataire de paiement branché pour l'instant).
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
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
    return () => dialog?.close()
  }, [])

  const value = custom ? Number(custom) : (amount ?? 0)
  const collection = collections.find((c) => c.id === collectionId)
  const channel = channels?.find((c) => c.id === channelId)
  const result = mutation.data

  const canContinue =
    (step === 1 && !!collection) ||
    (step === 2 && value > 0 && (anonymous || name.trim().length > 0)) ||
    (step === 3 && !!channel && !mutation.isPending)

  const reset = () => {
    mutation.reset()
    setStep(1)
    setAmount(null)
    setCustom('')
    setChannelId(null)
    setError(null)
  }

  const close = () => onClose()

  const next = async () => {
    setError(null)
    if (step === 2 && !(value > 0)) return setError(t('public.dialog.amountRequired'))
    if (step === 2 && !anonymous && !name.trim()) return setError(t('public.dialog.nameRequired'))
    if (step < 3) return setStep(step + 1)
    if (!collection || !channel) return
    try {
      await mutation.mutateAsync({
        projectId: collection.id,
        paymentChannelId: channel.id,
        amount: value,
        anonymous,
        donorName: anonymous ? undefined : name.trim(),
        donorContact: anonymous ? undefined : contact.trim() || undefined,
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
      {result ? (
        <div className={styles.success}>
          <span className={styles.ring} aria-hidden="true" />
          <IccLogo animated letterColor="#FFFFFF" className={styles.successLogo} />
          <h2 id="donation-title" className={styles.successTitle}>
            {t('public.dialog.successTitle')}
          </h2>
          <p className={styles.successText}>
            {t('public.dialog.successText', {
              amount: formatFcfa(value),
              collection: collection?.name ?? '',
              channel: channel?.label ?? '',
            })}
          </p>
          {PAYMENT_NUMBER && (
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
                <p className={styles.note}>{t('public.dialog.manualNote')}</p>
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
                  ? t('public.dialog.submitting')
                  : t('public.dialog.submit')}
            </button>
          </footer>
        </div>
      )}
    </dialog>
  )
}
