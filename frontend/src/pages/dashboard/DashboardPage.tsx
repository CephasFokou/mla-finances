import { useTranslation } from 'react-i18next'

import ui from '../../components/ui/ui.module.scss'
import { Icon } from '../../components/ui/Icon'
import { useReportsOverviewQuery } from '../../features/reports/hooks'
import { formatFcfa } from '../../lib/format'

const KPI_KEYS = ['balance', 'mobilized', 'disbursed', 'pendingWithdrawals'] as const

export function DashboardPage() {
  const { t } = useTranslation()
  const { data, isLoading, isError } = useReportsOverviewQuery()

  const values: Record<(typeof KPI_KEYS)[number], string> | null = data
    ? {
        balance: formatFcfa(data.globalBalance),
        mobilized: formatFcfa(data.mobilizedThisMonth),
        disbursed: formatFcfa(data.disbursedThisMonth),
        pendingWithdrawals: String(data.pendingWithdrawalsCount),
      }
    : null

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('dashboard.title')}</h1>
        </div>
      </header>

      {isError && (
        <div className={`${ui.banner} ${ui.bannerRed}`} style={{ marginBottom: 'var(--space-5)' }}>
          <Icon name="alert" size={16} />
          <div>{t('dashboard.error')}</div>
        </div>
      )}

      <div className={ui.kpiRow}>
        {KPI_KEYS.map((kpi) => (
          <div key={kpi} className={ui.card}>
            <div className={ui.kpiLabel}>
              <span>{t(`dashboard.kpi.${kpi}`)}</span>
            </div>
            <div className={ui.kpiValue}>
              {isLoading || !values ? (
                <span className={ui.skel} style={{ display: 'inline-block', width: 80, height: 24 }} />
              ) : (
                values[kpi]
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}
