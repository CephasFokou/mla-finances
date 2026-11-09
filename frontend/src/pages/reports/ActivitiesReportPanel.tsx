import { useTranslation } from 'react-i18next'

import ledger from '../../components/ledger/ledger.module.scss'
import ui from '../../components/ui/ui.module.scss'
import { useActivityReportQuery } from '../../features/ledger/hooks'
import { formatAmountCell, formatDate } from '../../lib/format'

/** Onglet « Activités » : ce que chaque activité a coûté à la caisse (dépenses − retours et dons). */
export function ActivitiesReportPanel() {
  const { t } = useTranslation()
  const { data, isLoading, isError } = useActivityReportQuery()
  const total = data?.reduce((sum, r) => sum + r.netCost, 0) ?? 0

  return (
    <div>
      {isLoading && <div className={ui.skel} style={{ height: 240 }} />}
      {isError && <div className={`${ui.banner} ${ui.bannerRed}`}>{t('reports.error')}</div>}
      {data && (
        <div className={`${ui.card} ${ui.cardFlush}`}>
          <div className={ui.tableWrap}>
            <table className={`${ui.table} ${ledger.denseTable}`}>
              <thead>
                <tr>
                  <th>{t('activities.name')}</th>
                  <th>{t('activities.date')}</th>
                  <th>{t('activities.status')}</th>
                  <th className={ui.num}>{t('reports.activities.expenses')}</th>
                  <th className={ui.num}>{t('reports.activities.returns')}</th>
                  <th className={ui.num}>{t('reports.activities.netCost')}</th>
                </tr>
              </thead>
              <tbody>
                {data.map((row) => (
                  <tr key={row.activity.id}>
                    <td>
                      <span className={ui.cellMain}>{row.activity.name}</span>
                      {row.activity.comment && <div className={ui.cellSub}>{row.activity.comment}</div>}
                    </td>
                    <td>
                      {row.activity.periodLabel ||
                        (row.activity.eventDate ? formatDate(row.activity.eventDate) : '–')}
                    </td>
                    <td>
                      <span
                        className={`${ui.pill} ${row.activity.status === 'COMPLETED' ? ui.pillGrey : ui.pillAmber}`}
                      >
                        {t(`activityStatus.${row.activity.status}`)}
                      </span>
                    </td>
                    <td className={ui.num}>{formatAmountCell(row.expenses)}</td>
                    <td className={ui.num}>{formatAmountCell(row.returns)}</td>
                    <td className={ui.num}>
                      <b>{formatAmountCell(row.netCost)}</b>
                    </td>
                  </tr>
                ))}
                <tr className={ledger.totalRow}>
                  <td colSpan={5}>{t('reports.activities.total')}</td>
                  <td className={ui.num}>{formatAmountCell(total)}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      )}
      <p className={ledger.note}>{t('reports.activities.note')}</p>
    </div>
  )
}
