import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import ledger from '../../components/ledger/ledger.module.scss'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useContributionsQuery } from '../../features/contributions/hooks'
import { useLedgerSummaryQuery } from '../../features/ledger/hooks'
import type { LedgerSummary } from '../../features/ledger/types'
import { useWithdrawalsQuery } from '../../features/withdrawals/hooks'
import { formatAmountCell, formatDate, formatDateTime, formatPercent, personLabel } from '../../lib/format'
import { useCountUp } from '../../lib/useCountUp'
import styles from './DashboardPage.module.scss'

const CURRENT_YEAR = new Date().getFullYear()
const MONTH_KEYS = [
  'jan',
  'feb',
  'mar',
  'apr',
  'may',
  'jun',
  'jul',
  'aug',
  'sep',
  'oct',
  'nov',
  'dec',
] as const
const amountFormatter = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 })

/**
 * Synthèse (charte ICC) : solde de la caisse du MLA avec sa courbe, collectes en cours,
 * contributions mensuelles avec la part des responsables, graphique mensuel et derniers
 * mouvements. Le seul moment animé du back-office : chiffres et barres au chargement.
 */
export function DashboardPage() {
  const { t } = useTranslation()
  const [year, setYear] = useState(CURRENT_YEAR)
  const { data, isLoading, isError } = useLedgerSummaryQuery(year)

  return (
    <section className={styles.page}>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('dashboard.title', { year })}</h1>
          {data && <p>{t('dashboard.generatedAt', { date: formatDateTime(data.generatedAt) })}</p>}
        </div>
        <div className={styles.headActions}>
          <label className={styles.yearField}>
            <span>{t('dashboard.year')}</span>
            <select value={year} onChange={(e) => setYear(Number(e.target.value))}>
              {[CURRENT_YEAR, CURRENT_YEAR - 1, CURRENT_YEAR - 2].map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </label>
          <Link to="/contributions/new" className={`${ui.btn} ${ui.btnPrimary}`}>
            <Icon name="plus" size={15} />
            {t('dashboard.actions.contribution')}
          </Link>
          <Link to="/withdrawals/new" className={`${ui.btn} ${ui.btnSecondary}`}>
            {t('dashboard.actions.expense')}
          </Link>
        </div>
      </header>

      {isError && (
        <div className={`${ui.banner} ${ui.bannerRed}`}>
          <Icon name="alert" size={16} />
          <div>{t('dashboard.error')}</div>
        </div>
      )}
      {isLoading && <div className={ui.skel} style={{ height: 300 }} />}
      {data && <SummaryView key={year} data={data} year={year} />}
    </section>
  )
}

function SummaryView({ data, year }: { data: LedgerSummary; year: number }) {
  const { t } = useTranslation()
  const caisse = useCountUp(data.mainFundBalance)
  const collections = useCountUp(data.openCollectionsBalance)
  const monthly = useCountUp(data.monthlyContributionsTotal)
  const share = data.yearTotals.responsablesShare
  const shareShown = useCountUp(share ?? 0)

  // La courbe s'arrête au mois en cours pour l'année courante (les mois futurs n'ont pas eu lieu).
  const lastMonth = year === CURRENT_YEAR ? new Date().getMonth() : 11
  const spark = useMemo(() => sparkPaths(data, lastMonth), [data, lastMonth])
  const maxTotal = Math.max(1, ...data.months.map((m) => m.total))
  const respPart = data.yearTotals.total > 0 ? data.yearTotals.responsablesAmount / data.yearTotals.total : 0

  return (
    <>
      <section className={styles.leadBand} aria-label={t('dashboard.kpi.mainFund')}>
        <div className={styles.leadMain}>
          <h2 className={styles.kLabel}>{t('dashboard.kpi.mainFund')}</h2>
          <div className={styles.kBig}>
            {amountFormatter.format(caisse)}
            <span> FCFA</span>
          </div>
          <p className={styles.kNote}>{t('dashboard.lead.curve')}</p>
          <svg viewBox="0 0 440 90" className={styles.spark} aria-hidden="true" preserveAspectRatio="none">
            <path d={spark.area} className={styles.sparkArea} />
            <path d={spark.line} className={styles.sparkLine} />
          </svg>
        </div>
        <div className={styles.leadSide}>
          <div className={styles.kBlock}>
            <h2 className={styles.kLabel}>{t('dashboard.kpi.openCollections')}</h2>
            <div className={styles.kMid}>
              {amountFormatter.format(collections)}
              <span> FCFA</span>
            </div>
            <p className={styles.kNote}>
              {t('dashboard.openCollectionsHint', { count: data.openCollectionsCount })}
            </p>
          </div>
          <div className={styles.kBlock}>
            <h2 className={styles.kLabel}>{t('dashboard.lead.monthlyTitle', { year })}</h2>
            <div className={styles.kMid}>
              {amountFormatter.format(monthly)}
              <span> FCFA</span>
            </div>
            <div
              className={styles.split}
              role="img"
              aria-label={t('dashboard.lead.splitLabel', {
                resp: formatAmountCell(data.yearTotals.responsablesAmount),
                members: formatAmountCell(data.yearTotals.membersAmount),
              })}
            >
              <span className={styles.splitResp} style={{ width: `${respPart * 100}%` }} />
              <span className={styles.splitMemb} style={{ width: `${(1 - respPart) * 100}%` }} />
            </div>
            <p className={styles.kNote}>
              <strong>
                {t('dashboard.lead.share', { share: formatPercent(share === null ? null : shareShown) })}
              </strong>{' '}
              {t('dashboard.lead.shareDetail', {
                contributors: data.yearTotals.responsablesContributors,
                count: data.activeResponsablesCount,
                members: data.yearTotals.membersContributors,
              })}
            </p>
          </div>
        </div>
      </section>

      <div className={styles.cols}>
        <section className={styles.panel} aria-labelledby="chart-title">
          <div className={styles.panelHead}>
            <h2 id="chart-title">{t('dashboard.monthly.title')}</h2>
            <div className={styles.legend}>
              <span>
                <i className={styles.legendResp} />
                {t('memberRole.plural.RESPONSABLE')}
              </span>
              <span>
                <i className={styles.legendMemb} />
                {t('memberRole.plural.MEMBRE')}
              </span>
            </div>
          </div>
          <div className={styles.chart} role="img" aria-label={t('dashboard.lead.chartLabel', { year })}>
            {data.months.map((m, i) => {
              const height = Math.round((m.total / maxTotal) * 200)
              const others = m.membersAmount + m.unattributedAmount
              return (
                <div key={m.month} className={styles.col}>
                  <span className={styles.colTotal}>{formatAmountCell(m.total)}</span>
                  <div
                    className={`${styles.bar} ${m.total ? '' : styles.barEmpty}`}
                    style={{ height: Math.max(height, 3), animationDelay: `${0.15 + i * 0.06}s` }}
                  >
                    {m.total > 0 && (
                      <>
                        <span className={styles.bMemb} style={{ height: `${(others / m.total) * 100}%` }} />
                        <span
                          className={styles.bResp}
                          style={{ height: `${(m.responsablesAmount / m.total) * 100}%` }}
                        />
                      </>
                    )}
                  </div>
                  <span className={styles.colLabel}>
                    {t(`dashboard.monthsShort.${MONTH_KEYS[i] ?? 'jan'}`)}
                  </span>
                </div>
              )
            })}
          </div>
          <p className={styles.kNote}>{t('dashboard.lead.chartNote')}</p>
        </section>

        <RecentMovements />
      </div>

      <MonthlyTable data={data} year={year} />
    </>
  )
}

/** Courbe du solde de la caisse en fin de mois, de janvier au dernier mois écoulé. */
function sparkPaths(data: LedgerSummary, lastMonth: number) {
  const values = data.months.slice(0, lastMonth + 1).map((m) => m.mainFundBalanceEndOfMonth ?? 0)
  if (values.length < 2) values.push(values[0] ?? 0)
  const max = Math.max(1, ...values)
  const min = Math.min(0, ...values)
  const width = 440
  const top = 8
  const bottom = 82
  const points = values.map((v, i) => [
    (i * width) / (values.length - 1),
    bottom - ((v - min) / (max - min)) * (bottom - top),
  ])
  const line = points.map(([x, y], i) => `${i ? 'L' : 'M'}${x?.toFixed(1)} ${y?.toFixed(1)}`).join(' ')
  return { line, area: `${line} L${width} 90 L0 90 Z` }
}

/** Les cinq derniers mouvements, entrées et sorties confondues, par date réelle. */
function RecentMovements() {
  const { t } = useTranslation()
  const { data: contributions } = useContributionsQuery({ size: 5, sort: 'transactionDate,desc' })
  const { data: withdrawals } = useWithdrawalsQuery({
    size: 5,
    sort: 'requestedAt,desc',
    status: 'PROCESSED',
  })

  const rows = useMemo(() => {
    const ins = (contributions?.content ?? []).map((c) => ({
      key: `c${c.id}`,
      to: `/contributions/${c.id}`,
      date: c.transactionDate,
      what: t(`nature.${c.nature}`),
      who: c.anonymous ? t('contributions.anonymous') : personLabel(c.donor),
      where: c.project?.name ?? c.category?.name ?? '',
      amount: c.amount,
    }))
    const outs = (withdrawals?.content ?? []).map((w) => ({
      key: `w${w.id}`,
      to: `/withdrawals/${w.id}`,
      date: w.operationDate ?? w.requestedAt.slice(0, 10),
      what: t(`nature.${w.nature}`),
      who: personLabel(w.person),
      where: w.project?.name ?? w.category?.name ?? '',
      amount: -w.amount,
    }))
    return [...ins, ...outs].sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0)).slice(0, 6)
  }, [contributions, withdrawals, t])

  return (
    <section className={styles.panel} aria-labelledby="ops-title">
      <div className={styles.panelHead}>
        <h2 id="ops-title">{t('dashboard.recent.title')}</h2>
        <Link to="/contributions" className={styles.link}>
          {t('dashboard.recent.all')}
        </Link>
      </div>
      {rows.length === 0 && <p className={styles.kNote}>{t('dashboard.recent.empty')}</p>}
      <ul className={styles.ops}>
        {rows.map((r) => (
          <li key={r.key}>
            <Link to={r.to} className={styles.op}>
              <span className={styles.opDate}>{formatDate(r.date)}</span>
              <span className={styles.opMain}>
                <span className={styles.opWhat}>{r.what}</span>
                <span className={styles.opSub}>{r.who !== '—' ? `${r.who}, ${r.where}` : r.where}</span>
              </span>
              <span className={`${styles.opAmt} ${r.amount < 0 ? styles.opOut : styles.opIn}`}>
                {r.amount < 0 ? '−' : '+'}
                {amountFormatter.format(Math.abs(r.amount))}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** Le détail mois par mois demandé par le R+1 : responsables et membres, part, solde de caisse. */
function MonthlyTable({ data, year }: { data: LedgerSummary; year: number }) {
  const { t } = useTranslation()
  const currentMonth = new Date().toISOString().slice(0, 7)
  return (
    <section className={styles.panel} aria-labelledby="table-title">
      <div className={styles.panelHead}>
        <h2 id="table-title">{t('dashboard.lead.tableTitle')}</h2>
      </div>
      <div className={ui.tableWrap}>
        <table className={`${ui.table} ${ledger.denseTable}`}>
          <thead>
            <tr>
              <th>{t('dashboard.monthly.month')}</th>
              <th className={ui.num}>{t('dashboard.monthly.responsables')}</th>
              <th className={ui.num}>{t('dashboard.monthly.responsablesAmount')}</th>
              <th className={ui.num}>{t('dashboard.monthly.members')}</th>
              <th className={ui.num}>{t('dashboard.monthly.membersAmount')}</th>
              <th className={ui.num}>{t('dashboard.monthly.total')}</th>
              <th className={ui.num}>{t('dashboard.monthly.share')}</th>
              <th className={ui.num}>{t('dashboard.monthly.balance')}</th>
            </tr>
          </thead>
          <tbody>
            {data.months.map((m, index) => {
              const future = m.month > currentMonth
              return (
                <tr key={m.month} className={future ? ledger.muted : undefined}>
                  <td>{t(`dashboard.months.${MONTH_KEYS[index] ?? 'jan'}`)}</td>
                  <td className={ui.num}>
                    {m.responsablesContributors} / {data.activeResponsablesCount}
                  </td>
                  <td className={ui.num}>{formatAmountCell(m.responsablesAmount)}</td>
                  <td className={ui.num}>{m.membersContributors}</td>
                  <td className={ui.num}>{formatAmountCell(m.membersAmount)}</td>
                  <td className={ui.num}>{formatAmountCell(m.total)}</td>
                  <td className={ui.num}>{formatPercent(m.responsablesShare)}</td>
                  <td className={ui.num}>
                    {future || m.mainFundBalanceEndOfMonth === null
                      ? '–'
                      : formatAmountCell(m.mainFundBalanceEndOfMonth)}
                  </td>
                </tr>
              )
            })}
            <tr className={ledger.totalRow}>
              <td>{t('dashboard.monthly.year', { year })}</td>
              <td className={ui.num}>
                {data.yearTotals.responsablesContributors} / {data.activeResponsablesCount}
              </td>
              <td className={ui.num}>{formatAmountCell(data.yearTotals.responsablesAmount)}</td>
              <td className={ui.num}>{data.yearTotals.membersContributors}</td>
              <td className={ui.num}>{formatAmountCell(data.yearTotals.membersAmount)}</td>
              <td className={ui.num}>{formatAmountCell(data.yearTotals.total)}</td>
              <td className={ui.num}>{formatPercent(data.yearTotals.responsablesShare)}</td>
              <td />
            </tr>
          </tbody>
        </table>
      </div>
      <p className={ledger.note}>{t('dashboard.monthly.note')}</p>
    </section>
  )
}
