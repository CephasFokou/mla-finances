import { useTranslation } from 'react-i18next'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useReportsOverviewQuery } from '../../features/reports/hooks'
import { formatDateTime, formatFcfa } from '../../lib/format'
import styles from './ReportsPage.module.scss'

const KPI_KEYS = [
  'globalBalance',
  'mobilizedThisMonth',
  'disbursedThisMonth',
  'pendingWithdrawalsCount',
] as const

export function OverviewPanel() {
  const { t } = useTranslation()
  const { data, isLoading, isError } = useReportsOverviewQuery()

  if (isLoading) {
    return (
      <div className={styles.skeletons} role="status" aria-label={t('reports.loading')}>
        {[100, 100, 100, 100].map((width, index) => (
          <div key={index} className={ui.skel} style={{ width: `${width}%`, height: 84 }} />
        ))}
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className={`${ui.card} ${ui.emptyState}`}>
        <div className={ui.emptyIcon}>
          <Icon name="alert" size={24} />
        </div>
        <h4>{t('reports.overview.error')}</h4>
      </div>
    )
  }

  return (
    <div>
      <p className={styles.generatedAt}>
        {t('reports.overview.generatedAt', { date: formatDateTime(data.generatedAt) })}
      </p>
      <div className={ui.kpiRow}>
        {KPI_KEYS.map((key) => (
          <div key={key} className={ui.card}>
            <div className={ui.kpiLabel}>
              <span>{t(`reports.overview.kpi.${key}`)}</span>
            </div>
            <div className={ui.kpiValue}>
              {key === 'pendingWithdrawalsCount' ? data[key] : formatFcfa(data[key])}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
