import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import ledger from '../../components/ledger/ledger.module.scss'
import ui from '../../components/ui/ui.module.scss'
import { useCollectionsReportQuery } from '../../features/ledger/hooks'
import { formatAmountCell, formatDate } from '../../lib/format'
import { ProjectStatusPill } from '../projects/ProjectStatusPill'

/** Onglet « Collectes » : chaque collecte dédiée, hors caisse du MLA (collecté / utilisé / reste). */
export function CollectionsPanel() {
  const { t } = useTranslation()
  const { data, isLoading, isError } = useCollectionsReportQuery()

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
                  <th>{t('reports.collections.name')}</th>
                  <th>{t('reports.collections.status')}</th>
                  <th>{t('reports.collections.period')}</th>
                  <th className={ui.num}>{t('reports.collections.count')}</th>
                  <th className={ui.num}>{t('reports.collections.collected')}</th>
                  <th className={ui.num}>{t('reports.collections.otherInflows')}</th>
                  <th className={ui.num}>{t('reports.collections.used')}</th>
                  <th className={ui.num}>{t('reports.collections.remaining')}</th>
                </tr>
              </thead>
              <tbody>
                {data.map((row) => (
                  <tr key={row.project.id}>
                    <td>
                      <Link to={`/projects/${row.project.id}`} className={ui.cellMain}>
                        {row.project.name}
                      </Link>
                      {row.description && <div className={ui.cellSub}>{row.description}</div>}
                    </td>
                    <td>
                      <ProjectStatusPill status={row.status} />
                    </td>
                    <td>
                      {row.firstMovementDate ? formatDate(row.firstMovementDate) : '–'}
                      {row.lastMovementDate && row.lastMovementDate !== row.firstMovementDate
                        ? ` → ${formatDate(row.lastMovementDate)}`
                        : ''}
                    </td>
                    <td className={ui.num}>{row.contributionsCount}</td>
                    <td className={ui.num}>{formatAmountCell(row.collected)}</td>
                    <td className={ui.num}>{formatAmountCell(row.otherInflows)}</td>
                    <td className={ui.num}>{formatAmountCell(row.used)}</td>
                    <td className={`${ui.num} ${row.remaining < 0 ? ledger.negative : ''}`}>
                      <b>{formatAmountCell(row.remaining)}</b>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      <p className={ledger.note}>{t('reports.collections.note')}</p>
    </div>
  )
}
