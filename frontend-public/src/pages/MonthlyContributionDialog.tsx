import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { usePublicPaymentChannelsQuery, useSubmitPublicDonationMutation } from '../features/public/hooks'
import { useOnlinePaymentTracking, usePaymentChoice } from '../features/public/payment'
import type { PublicMonthlyOffer } from '../features/public/types'
import { ApiError } from '../lib/apiClient'
import { currentMonthKey, formatFcfa, formatMonthList, formatMonthShort } from '../lib/format'
import styles from './DonationDialog.module.scss'
import { FailureScreen, FlowHeader, PaymentStep, SuccessScreen, WaitingScreen } from './PaymentParts'
import { ReceiptButton, type ReceiptData } from './Receipt'

const STEPS = 4
/** Numéro où envoyer le paiement, affiché après l'enregistrement s'il est configuré au déploiement. */
const PAYMENT_NUMBER: string | undefined = import.meta.env.VITE_MLA_PAYMENT_NUMBER

/** Comparaison sans accents ni casse, pour retrouver « Hélène » en tapant « hel ». */
function fold(value: string) {
  return value
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
}

/**
 * Contribution mensuelle d'un membre à la caisse MLA, en quatre étapes : 1) son nom dans la liste
 * (avec recherche), 2) les mois (mois courant coché), 3) le montant par mois prérempli selon son
 * rôle, total affiché, 4) le paiement — même écran que les dons. Jamais anonyme. Monté seulement
 * quand il est ouvert : chaque ouverture repart d'un parcours vierge.
 */
export function MonthlyContributionDialog({
  offer,
  onClose,
}: {
  offer: PublicMonthlyOffer
  onClose: () => void
}) {
  const { t } = useTranslation()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const { data: channels } = usePublicPaymentChannelsQuery()
  const mutation = useSubmitPublicDonationMutation()

  const defaultMonth = useMemo(() => {
    const now = currentMonthKey()
    // Mois courant d'après le téléphone ; à défaut (horloge décalée), celui du serveur (4e avant la fin).
    return offer.months.includes(now) ? now : offer.months[Math.max(0, offer.months.length - 4)]
  }, [offer.months])

  const [step, setStep] = useState(1)
  const [query, setQuery] = useState('')
  const [memberId, setMemberId] = useState<number | null>(null)
  const [months, setMonths] = useState<string[]>(defaultMonth ? [defaultMonth] : [])
  const [amount, setAmount] = useState('')
  const payment = usePaymentChoice(channels)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
    return () => dialog?.close()
  }, [])

  const member = offer.members.find((m) => m.id === memberId)
  const matches = useMemo(() => {
    const q = fold(query.trim())
    return q ? offer.members.filter((m) => fold(m.displayName).includes(q)) : offer.members
  }, [offer.members, query])
  const perMonth = /^\d+$/.test(amount) ? Number(amount) : 0
  const sortedMonths = [...months].sort()
  const total = perMonth * months.length
  const { channel, online } = payment
  const result = mutation.data
  const { liveStatus, failureMessage, longWait, receipt } = useOnlinePaymentTracking(result)
  const receiptData: ReceiptData | null = result
    ? {
        kind: 'MONTHLY_CONTRIBUTION',
        reference: result.reference,
        amount: receipt?.amount ?? total,
        purpose: receipt?.purpose ?? t('public.receipt.fundName'),
        months: receipt?.months?.length ? receipt.months : sortedMonths,
        donorName: receipt?.donorName ?? member?.displayName ?? null,
        channel: receipt?.channel ?? channel?.label ?? null,
        payerPhone: receipt?.payerPhone ?? null,
        confirmedAt: receipt?.confirmedAt ?? null,
      }
    : null

  const chooseMember = (id: number) => {
    setMemberId(id)
    // Le montant suit le membre choisi : on le repréremplit à chaque changement de nom.
    const suggested = offer.members.find((m) => m.id === id)?.suggestedAmount
    setAmount(suggested ? String(suggested) : '')
  }

  const toggleMonth = (key: string) =>
    setMonths((current) => (current.includes(key) ? current.filter((m) => m !== key) : [...current, key]))

  const canContinue =
    (step === 1 && !!member) ||
    (step === 2 && months.length > 0) ||
    (step === 3 && perMonth > 0) ||
    (step === 4 && payment.ready && !mutation.isPending)

  const retry = () => {
    mutation.reset()
    setError(null)
    setStep(4)
  }

  const next = async () => {
    setError(null)
    if (step === 1 && !member) return setError(t('public.monthly.dialog.memberRequired'))
    if (step === 2 && months.length === 0) return setError(t('public.monthly.dialog.monthsRequired'))
    if (step === 3 && !(perMonth > 0)) return setError(t('public.monthly.dialog.amountRequired'))
    if (step < STEPS) return setStep(step + 1)
    if (!member || !channel) return
    if (online && !payment.valid) return setError(t('public.dialog.phoneInvalid'))
    try {
      await mutation.mutateAsync({
        type: 'MONTHLY_CONTRIBUTION',
        donorId: member.id,
        months: sortedMonths,
        amountPerMonth: perMonth,
        paymentChannelId: channel.id,
        payerPhone: online ? payment.digits : undefined,
      })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('public.monthly.dialog.error'))
    }
  }

  const monthsText = formatMonthList(sortedMonths)

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby="donation-title"
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
    >
      {result && result.mode === 'ONLINE' && liveStatus === 'PROCESSING' ? (
        <WaitingScreen
          text={t('public.dialog.waitingText', {
            amount: formatFcfa(total),
            phone: payment.digits.replace(/^237/, ''),
            channel: channel?.label ?? '',
          })}
          longWait={longWait}
          reference={result.reference}
          onClose={onClose}
        />
      ) : result && result.mode === 'ONLINE' && (liveStatus === 'FAILED' || liveStatus === 'CANCELLED') ? (
        <FailureScreen
          message={failureMessage}
          reference={result.reference}
          onRetry={retry}
          onClose={onClose}
        />
      ) : result ? (
        <SuccessScreen
          title={
            result.mode === 'ONLINE'
              ? t('public.monthly.dialog.paidTitleNamed', { name: member?.displayName ?? '' })
              : t('public.monthly.dialog.successTitle')
          }
          reference={result.reference}
          actions={
            <>
              {result.mode === 'ONLINE' && receiptData && (
                <ReceiptButton receipt={receiptData} className={styles.ctaGold} />
              )}
              <button
                type="button"
                className={result.mode === 'ONLINE' ? styles.ctaGhost : styles.ctaGold}
                onClick={onClose}
              >
                {t('public.dialog.done')}
              </button>
            </>
          }
        >
          <p className={styles.successText}>
            {result.mode === 'ONLINE'
              ? t('public.monthly.dialog.paidText', { months: monthsText })
              : t('public.monthly.dialog.successText', {
                  amount: formatFcfa(total),
                  months: monthsText,
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
            steps={STEPS}
            stepLabel={t('public.monthly.dialog.step', { step })}
            title={t(`public.monthly.dialog.titles.${step}`)}
            onBack={() => setStep(Math.max(1, step - 1))}
            onClose={onClose}
          />

          <div className={styles.body} key={step}>
            {step === 1 && (
              <div className={styles.stack}>
                <label className={styles.field}>
                  {t('public.monthly.dialog.search')}
                  <input
                    type="search"
                    value={query}
                    autoComplete="off"
                    placeholder={t('public.monthly.dialog.searchPlaceholder')}
                    onChange={(e) => setQuery(e.target.value)}
                  />
                </label>
                <div className={styles.stack} role="radiogroup" aria-labelledby="donation-title">
                  {matches.map((m) => (
                    <button
                      key={m.id}
                      type="button"
                      role="radio"
                      aria-checked={memberId === m.id}
                      className={`${styles.choice} ${styles.choiceCompact}`}
                      onClick={() => chooseMember(m.id)}
                    >
                      <span className={styles.initial} aria-hidden="true">
                        {m.displayName.charAt(0).toUpperCase()}
                      </span>
                      <span className={styles.choiceTitle}>{m.displayName}</span>
                      <span className={styles.radio} />
                    </button>
                  ))}
                  {matches.length === 0 && (
                    <p className={styles.note}>{t('public.monthly.dialog.noMatch')}</p>
                  )}
                </div>
              </div>
            )}

            {step === 2 && (
              <div className={styles.stack}>
                <p className={styles.note} style={{ marginTop: 0 }}>
                  {t('public.monthly.dialog.monthsHint')}
                </p>
                <div className={styles.monthGrid} role="group" aria-labelledby="donation-title">
                  {offer.months.map((key) => (
                    <label key={key} className={styles.month}>
                      <input
                        type="checkbox"
                        checked={months.includes(key)}
                        onChange={() => toggleMonth(key)}
                      />
                      <span>{formatMonthShort(key)}</span>
                    </label>
                  ))}
                </div>
                <p className={styles.hint}>
                  {t('public.monthly.dialog.monthsSelected', { count: months.length })}
                </p>
              </div>
            )}

            {step === 3 && (
              <div className={styles.stack}>
                <label className={styles.field}>
                  {t('public.monthly.dialog.amountPerMonth')}
                  <input
                    inputMode="numeric"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value.replace(/\D/g, ''))}
                  />
                  <span className={styles.hint}>{t('public.monthly.dialog.amountHint')}</span>
                </label>
                <div className={styles.summary}>
                  <span>
                    {t('public.monthly.dialog.totalLabel')}
                    <br />
                    <small>
                      {t('public.monthly.dialog.total', {
                        count: months.length,
                        amount: formatFcfa(perMonth),
                      })}
                    </small>
                  </span>
                  <strong>{formatFcfa(total)}</strong>
                </div>
              </div>
            )}

            {step === 4 && (
              <div className={styles.stack}>
                <div className={styles.summary}>
                  <span>
                    {t('public.monthly.dialog.summary', { member: member?.displayName ?? '' })}
                    <br />
                    <small>{monthsText}</small>
                  </span>
                  <strong>{formatFcfa(total)}</strong>
                </div>
                <PaymentStep
                  choice={payment}
                  onlineNote={t('public.monthly.dialog.onlineNote')}
                  manualNote={t('public.monthly.dialog.manualNote')}
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
              {step < STEPS
                ? t('public.dialog.continue')
                : mutation.isPending
                  ? !payment.manualMode
                    ? t('public.dialog.paying')
                    : t('public.dialog.submitting')
                  : !payment.manualMode
                    ? t('public.dialog.pay', { amount: formatFcfa(total) })
                    : t('public.monthly.dialog.submit')}
            </button>
          </footer>
        </div>
      )}
    </dialog>
  )
}
