import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useProjectMonthlyQuery } from '../../features/reports/hooks'
import { useProjectsQuery } from '../../features/reference-data/hooks'
import { formatFcfa, formatMonth } from '../../lib/format'
import styles from './ReportsPage.module.scss'

const MONTHS = 6

export function ProjectMonthlyPanel() {
  const { t } = useTranslation()
  const { data: projects } = useProjectsQuery()
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>(undefined)

  // Tant que l'utilisateur n'a rien choisi explicitement, on affiche le premier projet de la
  // liste de référence (dérivé au rendu, pas via un effet, pour éviter un rendu en cascade).
  const projectId = selectedProjectId ?? projects?.[0]?.id

  const { data, isLoading, isError } = useProjectMonthlyQuery(projectId, MONTHS)

  const maxAmount = data ? Math.max(1, ...data.months.flatMap((m) => [m.contributions, m.withdrawals])) : 1

  return (
    <div>
      <div className={styles.projectPicker}>
        <label className={ui.field} style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <span>{t('reports.monthly.project')}</span>
          <select
            value={projectId ?? ''}
            onChange={(e) => setSelectedProjectId(e.target.value ? Number(e.target.value) : undefined)}
          >
            {!projects?.length && <option value="">{t('reports.monthly.noProjects')}</option>}
            {projects?.map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      {!projects?.length && (
        <div className={`${ui.card} ${ui.emptyState}`}>
          <div className={ui.emptyIcon}>
            <Icon name="folder" size={24} />
          </div>
          <h4>{t('reports.monthly.noProjects')}</h4>
        </div>
      )}

      {!!projects?.length && (
        <div className={ui.card}>
          {isLoading && (
            <div className={styles.skeletons} role="status" aria-label={t('reports.loading')}>
              <div className={ui.skel} style={{ height: 180 }} />
            </div>
          )}

          {isError && (
            <div className={ui.emptyState}>
              <div className={ui.emptyIcon}>
                <Icon name="alert" size={24} />
              </div>
              <h4>{t('reports.monthly.error')}</h4>
            </div>
          )}

          {!isLoading && !isError && data && (
            <>
              <div className={styles.chartLegend}>
                <span className={styles.legendItem}>
                  <span className={`${styles.legendSwatch} ${styles.swatchContrib}`} />
                  {t('reports.monthly.legendContributions')}
                </span>
                <span className={styles.legendItem}>
                  <span className={`${styles.legendSwatch} ${styles.swatchWithdraw}`} />
                  {t('reports.monthly.legendWithdrawals')}
                </span>
              </div>
              <div className={styles.chart}>
                {data.months.map((month) => (
                  <div key={month.month} className={styles.monthColumn}>
                    <div
                      className={styles.bars}
                      title={`${formatFcfa(month.contributions)} / ${formatFcfa(month.withdrawals)}`}
                    >
                      <div
                        className={`${styles.bar} ${styles.barContrib}`}
                        style={{ height: `${(month.contributions / maxAmount) * 100}%` }}
                      />
                      <div
                        className={`${styles.bar} ${styles.barWithdraw}`}
                        style={{ height: `${(month.withdrawals / maxAmount) * 100}%` }}
                      />
                    </div>
                    <span className={styles.monthLabel}>{formatMonth(month.month)}</span>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}
