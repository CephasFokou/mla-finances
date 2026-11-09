import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useActivitiesQuery, useCreateTransferMutation } from '../../features/ledger/hooks'
import { useCategoriesQuery, useProjectsQuery } from '../../features/reference-data/hooks'
import { ApiError } from '../../lib/apiClient'

const today = new Date().toISOString().slice(0, 10)

/** « p:12 » (collecte) ou « c:3 » (caisse/catégorie) : même convention que les filtres de liste. */
function envelope(value: string, side: 'from' | 'to') {
  const id = Number(value.slice(2))
  if (value.startsWith('p:')) return side === 'from' ? { fromProjectId: id } : { toProjectId: id }
  if (value.startsWith('c:')) return side === 'from' ? { fromCategoryId: id } : { toCategoryId: id }
  return {}
}

/**
 * RG-10 : transfert interne entre deux enveloppes (ex. reliquat d'une collecte reversé dans la
 * caisse, ou reliquat d'une collecte qui en complète une autre). Deux écritures liées sont créées
 * ensemble ; l'argent total détenu ne change pas.
 */
export function TransferFormPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: projects } = useProjectsQuery()
  const { data: categories } = useCategoriesQuery()
  const { data: activities } = useActivitiesQuery()
  const mutation = useCreateTransferMutation()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [amount, setAmount] = useState('')
  const [operationDate, setOperationDate] = useState(today)
  const [reason, setReason] = useState('')
  const [activityId, setActivityId] = useState('')
  const [error, setError] = useState<string | null>(null)

  // Les collectes encore actives, plus celles déjà clôturées (un reliquat peut être reversé après coup).
  const usableProjects = useMemo(() => projects?.filter((p) => p.status !== 'ARCHIVED') ?? [], [projects])
  const valid = from && to && from !== to && Number(amount) > 0 && reason.trim() && operationDate <= today
  const touchesMainFund = from.startsWith('c:') || to.startsWith('c:')

  const options = (
    <>
      <option value="">{t('withdrawals.form.selectPlaceholder')}</option>
      <optgroup label={t('ledger.mainFundOption')}>
        {categories?.map((c) => (
          <option key={c.id} value={`c:${c.id}`}>
            {c.name}
          </option>
        ))}
      </optgroup>
      <optgroup label={t('ledger.collectionOption')}>
        {usableProjects.map((p) => (
          <option key={p.id} value={`p:${p.id}`}>
            {p.name}
          </option>
        ))}
      </optgroup>
    </>
  )

  const submit = async () => {
    setError(null)
    try {
      await mutation.mutateAsync({
        ...envelope(from, 'from'),
        ...envelope(to, 'to'),
        amount: Number(amount),
        operationDate,
        reason,
        activityId: touchesMainFund && activityId ? Number(activityId) : undefined,
      })
      navigate('/withdrawals')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('transfers.genericError'))
    }
  }

  return (
    <section>
      <Link to="/withdrawals" className={ui.backLink}>
        <Icon name="arrowLeft" size={15} />
        {t('withdrawals.detail.back')}
      </Link>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('transfers.title')}</h1>
          <p>{t('transfers.subtitle')}</p>
        </div>
      </header>

      <form
        className={ui.card}
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          void submit()
        }}
      >
        <div className={ui.formGrid}>
          <label className={ui.field}>
            {t('transfers.from')}
            <select value={from} onChange={(e) => setFrom(e.target.value)}>
              {options}
            </select>
          </label>
          <label className={ui.field}>
            {t('transfers.to')}
            <select value={to} onChange={(e) => setTo(e.target.value)}>
              {options}
            </select>
            {from && from === to && <span className={ui.error}>{t('transfers.sameEnvelope')}</span>}
          </label>
          <label className={ui.field}>
            {t('withdrawals.form.amount')}
            <input
              type="number"
              min="0"
              step="1"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </label>
          <label className={ui.field}>
            {t('withdrawals.form.operationDate')}
            <input
              type="date"
              max={today}
              value={operationDate}
              onChange={(e) => setOperationDate(e.target.value)}
            />
          </label>
          {touchesMainFund && (
            <label className={ui.field}>
              {t('ledger.activity')}
              <select value={activityId} onChange={(e) => setActivityId(e.target.value)}>
                <option value="">{t('ledger.noActivity')}</option>
                {activities?.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label className={`${ui.field} ${ui.span2}`}>
            {t('withdrawals.form.reason')}
            <input
              value={reason}
              placeholder={t('transfers.reasonPlaceholder')}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
        </div>

        <div className={`${ui.banner} ${ui.bannerNavy}`}>
          <Icon name="swap" size={17} />
          <div>{t('transfers.notice')}</div>
        </div>
        {error && (
          <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
            <Icon name="alert" size={17} />
            <div>{error}</div>
          </div>
        )}
        <div className={ui.actions}>
          <Link to="/withdrawals" className={`${ui.btn} ${ui.btnSecondary}`}>
            {t('withdrawals.form.cancel')}
          </Link>
          <button
            type="submit"
            className={`${ui.btn} ${ui.btnPrimary}`}
            disabled={!valid || mutation.isPending}
          >
            {t('transfers.submit')}
          </button>
        </div>
      </form>
    </section>
  )
}
