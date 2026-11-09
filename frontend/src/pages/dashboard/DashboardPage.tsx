import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import ledger from '../../components/ledger/ledger.module.scss'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useLedgerSummaryQuery } from '../../features/ledger/hooks'
import { formatAmountCell, formatDateTime, formatFcfa, formatPercent } from '../../lib/format'

const CURRENT_YEAR = new Date().getFullYear()
interface Kpi {
  key: string
  value?: string
  hint?: string
}

const KPI_KEYS = ['mainFund', 'openCollections', 'monthlyTotal', 'responsablesShare']
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

/**
 * Synthèse : reprend l'onglet « Synthèse » du fichier de suivi — solde de la caisse du MLA (sans
 * les collectes), collectes en cours, contributions mensuelles de l'année et, mois par mois, la
 * part des responsables (statistique demandée chaque mois par le R+1).
 */
export function DashboardPage() {
  const { t } = useTranslation()
  const [year, setYear] = useState(CURRENT_YEAR)
  const { data, isLoading, isError } = useLedgerSummaryQuery(year)
  const currentMonth = new Date().toISOString().slice(0, 7)

  const kpis: Kpi[] = data
    ? [
        { key: 'mainFund', value: formatFcfa(data.mainFundBalance), hint: data.mainFund?.name },
        {
          key: 'openCollections',
          value: formatFcfa(data.openCollectionsBalance),
          hint: t('dashboard.openCollectionsHint', { count: data.openCollectionsCount }),
        },
        {
          key: 'monthlyTotal',
          value: formatFcfa(data.monthlyContributionsTotal),
          hint: t('dashboard.monthlyTotalHint', { year }),
        },
        {
          key: 'responsablesShare',
          value: formatPercent(data.yearTotals.responsablesShare),
          hint: t('dashboard.responsablesShareHint', {
            contributors: data.yearTotals.responsablesContributors,
            count: data.activeResponsablesCount,
          }),
        },
      ]
    : KPI_KEYS.map((key) => ({ key }))

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('dashboard.title', { year })}</h1>
          {data && <p>{t('dashboard.generatedAt', { date: formatDateTime(data.generatedAt) })}</p>}
        </div>
        <div className={ui.actions}>
          <label className={ui.field}>
            {t('dashboard.year')}
            <select value={year} onChange={(e) => setYear(Number(e.target.value))}>
              {[CURRENT_YEAR, CURRENT_YEAR - 1, CURRENT_YEAR - 2].map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </label>
        </div>
      </header>

      {isError && (
        <div className={`${ui.banner} ${ui.bannerRed}`} style={{ marginBottom: 'var(--space-5)' }}>
          <Icon name="alert" size={16} />
          <div>{t('dashboard.error')}</div>
        </div>
      )}

      <div className={ui.kpiRow}>
        {kpis.map((kpi) => (
          <div key={kpi.key} className={ui.card}>
            <div className={ui.kpiLabel}>
              <span>{t(`dashboard.kpi.${kpi.key}`)}</span>
            </div>
            <div className={ui.kpiValue}>
              {isLoading || kpi.value === undefined ? (
                <span className={ui.skel} style={{ display: 'inline-block', width: 80, height: 24 }} />
              ) : (
                kpi.value
              )}
            </div>
            {kpi.hint && <div className={ui.sub}>{kpi.hint}</div>}
          </div>
        ))}
      </div>

      <h2 className={ledger.sectionTitle}>{t('dashboard.monthly.title')}</h2>
      <div className={`${ui.card} ${ui.cardFlush}`}>
        {isLoading && <div className={ui.skel} style={{ height: 240, margin: 'var(--space-4)' }} />}
        {data && (
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
        )}
      </div>
      <p className={ledger.note}>{t('dashboard.monthly.note')}</p>

      <div className={ui.actions} style={{ marginTop: 'var(--space-5)' }}>
        <Link to="/contributions/new" className={`${ui.btn} ${ui.btnPrimary}`}>
          <Icon name="plus" size={15} />
          {t('dashboard.actions.contribution')}
        </Link>
        <Link to="/withdrawals/new" className={`${ui.btn} ${ui.btnSecondary}`}>
          {t('dashboard.actions.expense')}
        </Link>
        <Link to="/reports" className={`${ui.btn} ${ui.btnSecondary}`}>
          {t('dashboard.actions.reports')}
        </Link>
      </div>
    </section>
  )
}
