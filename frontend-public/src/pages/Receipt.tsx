import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'

import { IccLogo } from '../components/IccLogo'
import { formatFcfa, formatMonthList } from '../lib/format'
import styles from './Receipt.module.scss'

/** Contenu du reçu : celui de l'API (statut du don), complété par ce que le parcours connaît déjà. */
export interface ReceiptData {
  kind: 'DONATION' | 'MONTHLY_CONTRIBUTION'
  reference: string
  amount: number
  purpose: string
  months: string[]
  /** null : don anonyme. */
  donorName: string | null
  channel: string | null
  payerPhone: string | null
  confirmedAt: string | null
}

function formatDateTime(iso: string | null, language: string) {
  const date = iso ? new Date(iso) : new Date()
  return new Intl.DateTimeFormat(language === 'en' ? 'en-GB' : 'fr-FR', {
    dateStyle: 'long',
    timeStyle: 'short',
    timeZone: 'Africa/Douala',
  }).format(date)
}

/**
 * Bouton « Imprimer mon reçu » : le reçu est rendu hors de la fenêtre de don (directement dans
 * body) et seul lui s'imprime (voir global.scss). Sur téléphone, la feuille d'impression permet aussi
 * de l'enregistrer en PDF ou de le partager.
 */
export function ReceiptButton({ receipt, className }: { receipt: ReceiptData; className?: string }) {
  const { t } = useTranslation()
  const [host] = useState(() => {
    const div = document.createElement('div')
    div.className = 'mla-receipt'
    return div
  })

  useEffect(() => {
    document.body.appendChild(host)
    return () => host.remove()
  }, [host])

  return (
    <>
      <button type="button" className={className} onClick={() => window.print()}>
        {t('public.receipt.print')}
      </button>
      {createPortal(<Receipt receipt={receipt} />, host)}
    </>
  )
}

function Receipt({ receipt }: { receipt: ReceiptData }) {
  const { t, i18n } = useTranslation()
  const monthly = receipt.kind === 'MONTHLY_CONTRIBUTION'
  const rows: [string, string][] = [
    [t('public.receipt.reference'), receipt.reference],
    [t('public.receipt.date'), formatDateTime(receipt.confirmedAt, i18n.language)],
    [t('public.receipt.donor'), receipt.donorName ?? t('public.receipt.anonymous')],
    [monthly ? t('public.receipt.fund') : t('public.receipt.collection'), receipt.purpose],
  ]
  if (monthly && receipt.months.length > 0) {
    rows.push([t('public.receipt.months'), formatMonthList(receipt.months)])
  }
  if (receipt.channel) {
    rows.push([
      t('public.receipt.channel'),
      receipt.payerPhone ? `${receipt.channel} · ${receipt.payerPhone}` : receipt.channel,
    ])
  }

  return (
    <article className={styles.receipt}>
      <header className={styles.head}>
        <span className={styles.logo}>
          <IccLogo />
        </span>
        <div>
          <strong className={styles.org}>{t('public.footer.name')}</strong>
          <span className={styles.sub}>{t('public.receipt.site')}</span>
        </div>
      </header>

      <h1 className={styles.title}>
        {monthly ? t('public.receipt.titleMonthly') : t('public.receipt.title')}
      </h1>

      <div className={styles.amount}>
        <span>{t('public.receipt.amount')}</span>
        <strong>{formatFcfa(receipt.amount)}</strong>
      </div>

      <dl className={styles.rows}>
        {rows.map(([label, value]) => (
          <div key={label} className={styles.row}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
        <div className={styles.row}>
          <dt>{t('public.receipt.status')}</dt>
          <dd className={styles.ok}>{t('public.receipt.confirmed')}</dd>
        </div>
      </dl>

      <p className={styles.thanks}>{t('public.receipt.thanks')}</p>
      <p className={styles.foot}>{t('public.receipt.foot')}</p>
    </article>
  )
}
