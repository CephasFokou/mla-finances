import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import ui from '../../components/ui/ui.module.scss'
import { ActivitiesReportPanel } from './ActivitiesReportPanel'
import { AuditLogPanel } from './AuditLogPanel'
import { CalendarPanel } from './CalendarPanel'
import { CollectionsPanel } from './CollectionsPanel'
import { OverviewPanel } from './OverviewPanel'
import { ProjectMonthlyPanel } from './ProjectMonthlyPanel'
import styles from './ReportsPage.module.scss'

type Tab = 'calendar' | 'collections' | 'activities' | 'overview' | 'monthly' | 'audit'

export function ReportsPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<Tab>('calendar')

  const tabs: { key: Tab; label: string }[] = [
    { key: 'calendar', label: t('reports.tabs.calendar') },
    { key: 'collections', label: t('reports.tabs.collections') },
    { key: 'activities', label: t('reports.tabs.activities') },
    { key: 'overview', label: t('reports.tabs.overview') },
    { key: 'monthly', label: t('reports.tabs.monthly') },
    { key: 'audit', label: t('reports.tabs.audit') },
  ]

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('reports.title')}</h1>
        </div>
      </header>

      <div className={ui.seg} role="tablist" aria-label={t('reports.title')}>
        {tabs.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            aria-selected={tab === item.key}
            className={tab === item.key ? ui.segActive : undefined}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className={styles.tabPanel}>
        {tab === 'calendar' && <CalendarPanel />}
        {tab === 'collections' && <CollectionsPanel />}
        {tab === 'activities' && <ActivitiesReportPanel />}
        {tab === 'overview' && <OverviewPanel />}
        {tab === 'monthly' && <ProjectMonthlyPanel />}
        {tab === 'audit' && <AuditLogPanel />}
      </div>
    </section>
  )
}
