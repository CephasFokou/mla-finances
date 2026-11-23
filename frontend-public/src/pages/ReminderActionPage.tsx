import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { IccLogo } from '../components/IccLogo'
import { useAnswerReminderMutation, useReminderActionQuery } from '../features/public/hooks'
import type { ReminderAction, ReminderDateOption } from '../features/public/types'
import { ApiError } from '../lib/apiClient'
import styles from './ReminderActionPage.module.scss'

type Panel = 'choose' | 'dates'

/**
 * Page de réponse d'un email de relance (/relance/{jeton}) : « Bien reçu » (puis choix de la date) ou « C'est déjà
 * fait ». Rien n'est envoyé à l'ouverture de la page (les messageries ouvrent parfois les liens pour les analyser) :
 * le membre confirme toujours d'un clic. Aucun montant ni contact affiché.
 */
export function ReminderActionPage({ token, initial }: { token: string; initial: string | null }) {
  const { t } = useTranslation()
  const { data, isLoading, isError } = useReminderActionQuery(token)
  const mutation = useAnswerReminderMutation(token)
  const [panel, setPanel] = useState<Panel>(initial === 'ack' ? 'dates' : 'choose')
  const [date, setDate] = useState<string>('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dates, setDates] = useState<ReminderDateOption[] | null>(null)

  const answer = async (action: ReminderAction, chosen?: string) => {
    setError(null)
    try {
      const result = await mutation.mutateAsync({ action, date: chosen })
      if (action === 'ACK' && result.dateOptions.length > 0) {
        setDates(result.dateOptions)
        setPanel('dates')
        return
      }
      setMessage(result.message ?? t('public.reminderPage.thanks'))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('public.reminderPage.error'))
    }
  }

  const options = dates ?? data?.dateOptions ?? []

  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <div className={styles.head}>
          <span className={styles.logo}>
            <IccLogo letterColor="#FFFFFF" />
          </span>
          <span>{t('public.reminderPage.brand')}</span>
        </div>
        <div className={styles.body}>
          {isLoading && <p className={styles.muted}>{t('public.reminderPage.loading')}</p>}
          {isError && (
            <>
              <h1>{t('public.reminderPage.invalidTitle')}</h1>
              <p>{t('public.reminderPage.invalidText')}</p>
              <a className={styles.primary} href="/">
                {t('public.reminderPage.toSite')}
              </a>
            </>
          )}
          {data && message && (
            <>
              <h1>{t('public.reminderPage.doneTitle')}</h1>
              <p>{message}</p>
              <a className={styles.secondary} href="/">
                {t('public.reminderPage.toSite')}
              </a>
            </>
          )}
          {data && !message && (
            <>
              <h1>{t('public.reminderPage.hello', { name: data.name })}</h1>
              {data.settled ? (
                <p>{t('public.reminderPage.settled', { month: data.monthLabel })}</p>
              ) : !data.open ? (
                <p>{t('public.reminderPage.closed', { month: data.monthLabel })}</p>
              ) : panel === 'choose' ? (
                <>
                  <p>{t('public.reminderPage.question', { month: data.monthLabel })}</p>
                  <div className={styles.actions}>
                    <button
                      type="button"
                      className={styles.primary}
                      disabled={mutation.isPending}
                      onClick={() => void answer('ACK')}
                    >
                      {t('public.reminderPage.ack')}
                    </button>
                    <button
                      type="button"
                      className={styles.secondary}
                      disabled={mutation.isPending}
                      onClick={() => void answer('DONE')}
                    >
                      {t('public.reminderPage.done')}
                    </button>
                  </div>
                  {initial === 'done' && <p className={styles.muted}>{t('public.reminderPage.doneHint')}</p>}
                </>
              ) : (
                <>
                  <p>{t('public.reminderPage.when', { month: data.monthLabel })}</p>
                  <fieldset className={styles.dates}>
                    <legend className={styles.srOnly}>
                      {t('public.reminderPage.when', { month: data.monthLabel })}
                    </legend>
                    {options.map((o) => (
                      <label key={o.date} className={styles.option}>
                        <input
                          type="radio"
                          name="date"
                          value={o.date}
                          checked={date === o.date}
                          onChange={() => setDate(o.date)}
                        />
                        <span>{o.label}</span>
                      </label>
                    ))}
                    <label className={styles.option}>
                      <input
                        type="radio"
                        name="date"
                        value=""
                        checked={date === 'unsure'}
                        onChange={() => setDate('unsure')}
                      />
                      <span>{t('public.reminderPage.unsure')}</span>
                    </label>
                  </fieldset>
                  <div className={styles.actions}>
                    <button
                      type="button"
                      className={styles.primary}
                      disabled={!date || mutation.isPending}
                      onClick={() => void (date === 'unsure' ? answer('UNSURE') : answer('PROMISE', date))}
                    >
                      {t('public.reminderPage.confirm')}
                    </button>
                    <button type="button" className={styles.link} onClick={() => setPanel('choose')}>
                      {t('public.reminderPage.back')}
                    </button>
                  </div>
                </>
              )}
              {error && (
                <p className={styles.error} role="alert">
                  {error}
                </p>
              )}
            </>
          )}
        </div>
        <p className={styles.foot}>{t('public.reminderPage.foot')}</p>
      </div>
    </main>
  )
}
