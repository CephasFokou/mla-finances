import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useAuditLogQuery } from '../../features/reports/hooks'
import type { AuditAction, AuditEntityType, AuditLogFilters } from '../../features/reports/types'
import { formatDateTime } from '../../lib/format'
import styles from './ReportsPage.module.scss'

const PAGE_SIZE = 20
const ENTITY_TYPES: AuditEntityType[] = ['CONTRIBUTION', 'WITHDRAWAL', 'PROJECT', 'CATEGORY']
const ACTIONS: AuditAction[] = [
  'CREATE',
  'APPROVE',
  'REJECT',
  'CANCEL',
  'PROCESS',
  'ACTIVATE',
  'COMPLETE',
  'ARCHIVE',
  'DEACTIVATE',
]

function pillClassFor(action: AuditAction): string | undefined {
  switch (action) {
    case 'CREATE':
    case 'APPROVE':
    case 'PROCESS':
    case 'ACTIVATE':
    case 'COMPLETE':
      return ui.pillGreen
    case 'REJECT':
      return ui.pillRed
    case 'CANCEL':
    case 'DEACTIVATE':
      return ui.pillAmber
    default:
      return ui.pillGrey
  }
}

export function AuditLogPanel() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [entityType, setEntityType] = useState('')
  const [action, setAction] = useState('')

  const filters: AuditLogFilters = {
    entityType: (entityType || undefined) as AuditEntityType | undefined,
    action: (action || undefined) as AuditAction | undefined,
    page,
    size: PAGE_SIZE,
    sort: 'occurredAt,desc',
  }
  const { data, isLoading, isError, isFetching } = useAuditLogQuery(filters)
  const hasFilters = !!(entityType || action)

  const setFilter = (setter: (value: string) => void) => (value: string) => {
    setPage(0)
    setter(value)
  }

  const resetFilters = () => {
    setPage(0)
    setEntityType('')
    setAction('')
  }

  return (
    <div>
      <div className={styles.toolbar}>
        <label className={styles.filter}>
          <Icon name="filter" size={12} />
          <span>{t('reports.audit.filters.entityType')}</span>
          <select value={entityType} onChange={(e) => setFilter(setEntityType)(e.target.value)}>
            <option value="">{t('reports.audit.filters.all')}</option>
            {ENTITY_TYPES.map((type) => (
              <option key={type} value={type}>
                {t(`reports.audit.entityType.${type}`)}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.filter}>
          <span>{t('reports.audit.filters.action')}</span>
          <select value={action} onChange={(e) => setFilter(setAction)(e.target.value)}>
            <option value="">{t('reports.audit.filters.allFeminine')}</option>
            {ACTIONS.map((a) => (
              <option key={a} value={a}>
                {t(`reports.audit.action.${a}`)}
              </option>
            ))}
          </select>
        </label>
        {hasFilters && (
          <button type="button" className={styles.resetLink} onClick={resetFilters}>
            {t('reports.audit.filters.reset')}
          </button>
        )}
      </div>

      <div className={`${ui.card} ${ui.cardFlush}`}>
        {isLoading && (
          <div className={styles.skeletons} role="status" aria-label={t('reports.loading')}>
            {[100, 100, 92, 96, 88].map((width, index) => (
              <div key={index} className={ui.skel} style={{ width: `${width}%` }} />
            ))}
          </div>
        )}

        {isError && (
          <div className={ui.emptyState}>
            <div className={ui.emptyIcon}>
              <Icon name="alert" size={24} />
            </div>
            <h4>{t('reports.audit.error')}</h4>
          </div>
        )}

        {!isLoading && !isError && data?.content.length === 0 && (
          <div className={ui.emptyState}>
            <div className={ui.emptyIcon}>
              <Icon name="clock" size={24} />
            </div>
            <h4>{t('reports.audit.empty')}</h4>
            {hasFilters && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                onClick={resetFilters}
              >
                {t('reports.audit.filters.reset')}
              </button>
            )}
          </div>
        )}

        {!isLoading && !isError && data && data.content.length > 0 && (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <thead>
                <tr>
                  <th>{t('reports.audit.columns.occurredAt')}</th>
                  <th>{t('reports.audit.columns.action')}</th>
                  <th>{t('reports.audit.columns.summary')}</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((entry) => (
                  <tr key={entry.id}>
                    <td className={ui.cellSub}>{formatDateTime(entry.occurredAt)}</td>
                    <td>
                      <span className={`${ui.pill} ${pillClassFor(entry.action)}`}>
                        {t(`reports.audit.action.${entry.action}`)}
                      </span>
                    </td>
                    <td className={ui.cellMain}>{entry.summary}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className={styles.pagination}>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
            disabled={page === 0 || isFetching}
            onClick={() => setPage((p) => p - 1)}
          >
            {t('reports.audit.pagination.previous')}
          </button>
          <span>{t('reports.audit.pagination.pageInfo', { page: page + 1, total: data.totalPages })}</span>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
            disabled={page + 1 >= data.totalPages || isFetching}
            onClick={() => setPage((p) => p + 1)}
          >
            {t('reports.audit.pagination.next')}
          </button>
        </div>
      )}
    </div>
  )
}
