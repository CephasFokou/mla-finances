import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import ledger from '../../components/ledger/ledger.module.scss'
import ui from '../../components/ui/ui.module.scss'
import { useMonthlyCalendarQuery } from '../../features/ledger/hooks'
import type { CalendarRow } from '../../features/ledger/types'
import { formatAmountCell } from '../../lib/format'

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

/** Onglet « Calendrier » : montant versé par chaque membre pour chaque mois d'attribution. */
export function CalendarPanel() {
  const { t } = useTranslation()
  const [year, setYear] = useState(CURRENT_YEAR)
  const { data, isLoading, isError } = useMonthlyCalendarQuery(year)

  const groups: { key: 'RESPONSABLE' | 'MEMBRE'; rows: CalendarRow[] }[] = data
    ? [
        { key: 'RESPONSABLE', rows: data.rows.filter((r) => r.memberRole === 'RESPONSABLE') },
        { key: 'MEMBRE', rows: data.rows.filter((r) => r.memberRole === 'MEMBRE') },
      ]
    : []
  const hasUnattributed = !!data?.unattributed.some((a) => a !== 0)

  return (
    <div>
      <div className={ledger.toolbar}>
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
      {isLoading && <div className={ui.skel} style={{ height: 320 }} />}
      {isError && <div className={`${ui.banner} ${ui.bannerRed}`}>{t('reports.error')}</div>}
      {data && (
        <div className={`${ui.card} ${ui.cardFlush}`}>
          <div className={ui.tableWrap}>
            <table className={`${ui.table} ${ledger.denseTable}`}>
              <thead>
                <tr>
                  <th>{t('reports.calendar.member')}</th>
                  {MONTH_KEYS.map((m) => (
                    <th key={m} className={ui.num}>
                      {t(`dashboard.monthsShort.${m}`)}
                    </th>
                  ))}
                  <th className={ui.num}>{t('reports.calendar.total')}</th>
                  <th className={ui.num}>{t('reports.calendar.monthsCount')}</th>
                </tr>
              </thead>
              <tbody>
                {groups.map((group) => (
                  <GroupRows key={group.key} label={t(`memberRole.plural.${group.key}`)} rows={group.rows} />
                ))}
                {hasUnattributed && (
                  <tr>
                    <td>{t('reports.calendar.unattributed')}</td>
                    {data.unattributed.map((amount, i) => (
                      <td key={i} className={ui.num}>
                        {formatAmountCell(amount)}
                      </td>
                    ))}
                    <td className={ui.num}>
                      {formatAmountCell(data.unattributed.reduce((a, b) => a + b, 0))}
                    </td>
                    <td />
                  </tr>
                )}
                <tr className={ledger.totalRow}>
                  <td>{t('reports.calendar.total')}</td>
                  {data.totals.map((amount, i) => (
                    <td key={i} className={ui.num}>
                      {formatAmountCell(amount)}
                    </td>
                  ))}
                  <td className={ui.num}>{formatAmountCell(data.totals.reduce((a, b) => a + b, 0))}</td>
                  <td />
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      )}
      <p className={ledger.note}>{t('reports.calendar.note')}</p>
    </div>
  )
}

function GroupRows({ label, rows }: { label: string; rows: CalendarRow[] }) {
  const { t } = useTranslation()
  const subtotal = Array.from({ length: 12 }, (_, i) => rows.reduce((sum, r) => sum + (r.amounts[i] ?? 0), 0))
  return (
    <>
      <tr className={ledger.groupRow}>
        <td>{label}</td>
        {subtotal.map((amount, i) => (
          <td key={i} className={ui.num}>
            {formatAmountCell(amount)}
          </td>
        ))}
        <td className={ui.num}>{formatAmountCell(subtotal.reduce((a, b) => a + b, 0))}</td>
        <td />
      </tr>
      {rows.map((row) => (
        <tr key={row.donorId} className={row.memberStatus !== 'ACTIVE' ? ledger.muted : undefined}>
          <td>
            {row.displayName}
            {row.memberStatus && row.memberStatus !== 'ACTIVE' && (
              <span className={ui.cellSub}> · {t(`memberStatus.${row.memberStatus}`)}</span>
            )}
          </td>
          {row.amounts.map((amount, i) => (
            <td key={i} className={ui.num}>
              {formatAmountCell(amount)}
            </td>
          ))}
          <td className={ui.num}>{formatAmountCell(row.total)}</td>
          <td className={ui.num}>{row.monthsCount}</td>
        </tr>
      ))}
    </>
  )
}
