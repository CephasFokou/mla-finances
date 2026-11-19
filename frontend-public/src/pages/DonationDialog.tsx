import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { usePublicPaymentChannelsQuery, useSubmitPublicDonationMutation } from '../features/public/hooks'
import { phoneDigitsOf, useOnlinePaymentTracking } from '../features/public/payment'
import type { PublicCollection } from '../features/public/types'
import { ApiError } from '../lib/apiClient'
import { formatFcfa } from '../lib/format'
import styles from './DonationDialog.module.scss'
import { ChannelPicker, FailureScreen, FlowHeader, SuccessScreen, WaitingScreen } from './PaymentParts'

const AMOUNT_PRESETS = [1000, 2000, 5000, 10000]
/** Numéro où envoyer le paiement, affiché après l'enregistrement s'il est configuré au déploiement. */
const PAYMENT_NUMBER: string | undefined = import.meta.env.VITE_MLA_PAYMENT_NUMBER

interface DonationDialogProps {
  onClose: () => void
  collections: PublicCollection[]
  ribbonColor: (id: number) => string
  initialCollectionId: number | null
  /** Affiche en tête de l'étape 1 l'option « contribution mensuelle » (offre activée). */
  showMonthly?: boolean
  /** Ferme ce parcours et ouvre celui de la contribution mensuelle. */
  onChooseMonthly?: () => void
}

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
  showMonthly = false,
  onChooseMonthly,
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
  const { digits: phoneDigits, valid: phoneLooksValid } = phoneDigitsOf(phone)

  // Suivi d'un don en ligne tant qu'il n'est pas final (voir useOnlinePaymentTracking).
  const { liveStatus, failureMessage, longWait } = useOnlinePaymentTracking(result)

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
        <WaitingScreen
          text={t('public.dialog.waitingText', {
            amount: formatFcfa(value),
            phone: phoneDigits.replace(/^237/, ''),
            channel: channel?.label ?? '',
          })}
          longWait={longWait}
          reference={result.reference}
          onClose={close}
        />
      ) : result && result.mode === 'ONLINE' && (liveStatus === 'FAILED' || liveStatus === 'CANCELLED') ? (
        <FailureScreen
          message={failureMessage}
          reference={result.reference}
          onRetry={retry}
          onClose={close}
        />
      ) : result ? (
        <SuccessScreen
          title={result.mode === 'ONLINE' ? t('public.dialog.paidTitle') : t('public.dialog.successTitle')}
          reference={result.reference}
          actions={
            <>
              <button type="button" className={styles.ctaGold} onClick={reset}>
                {t('public.dialog.again')}
              </button>
              <button type="button" className={styles.ctaGhost} onClick={close}>
                {t('public.dialog.done')}
              </button>
            </>
          }
        >
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
        </SuccessScreen>
      ) : (
        <div className={styles.flow}>
          <FlowHeader
            step={step}
            steps={3}
            stepLabel={t('public.dialog.step', { step })}
            title={t(`public.dialog.titles.${step}`)}
            onBack={() => setStep(Math.max(1, step - 1))}
            onClose={close}
          />

          <div className={styles.body} key={step}>
            {step === 1 && showMonthly && onChooseMonthly && (
              <button type="button" className={styles.monthlyChoice} onClick={onChooseMonthly}>
                <span className={styles.choiceText}>
                  <span className={styles.monthlyTitle}>{t('public.dialog.monthlyOption')}</span>
                  <span className={styles.monthlySub}>{t('public.dialog.monthlyOptionSub')}</span>
                </span>
                <svg viewBox="0 0 24 24" className={styles.monthlyArrow} aria-hidden="true">
                  <path d="M9 5l7 7-7 7" />
                </svg>
              </button>
            )}
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
                {collections.length > 0 ? (
                  <p className={styles.note}>{t('public.dialog.separate')}</p>
                ) : (
                  <p className={styles.note}>{t('public.collections.empty')}</p>
                )}
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
                <ChannelPicker
                  channels={channels}
                  channelId={channelId}
                  onSelect={setChannelId}
                  phone={phone}
                  onPhoneChange={setPhone}
                  note={online ? t('public.dialog.onlineNote') : t('public.dialog.manualNote')}
                />
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
