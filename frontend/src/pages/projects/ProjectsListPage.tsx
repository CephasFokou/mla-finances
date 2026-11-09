import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useProjectsAdminQuery } from '../../features/projects/hooks'
import { formatDate, formatFcfa } from '../../lib/format'
import { ActivitiesPanel } from './ActivitiesPanel'
import { CategoriesPanel } from './CategoriesPanel'
import { ProjectStatusPill } from './ProjectStatusPill'
import styles from './ProjectsListPage.module.scss'

type Tab = 'projects' | 'categories' | 'activities'

function ProjectsTable() {
  const { t } = useTranslation()
  const { data: projects, isLoading, isError } = useProjectsAdminQuery()

  return (
    <div className={`${ui.card} ${ui.cardFlush}`}>
      {isLoading && (
        <div className={styles.skeletons}>
          <div className={ui.skel} />
          <div className={ui.skel} />
          <div className={ui.skel} />
        </div>
      )}
      {isError && (
        <div className={ui.emptyState}>
          <div className={ui.emptyIcon}>
            <Icon name="alert" size={24} />
          </div>
          <h4>{t('projects.error')}</h4>
        </div>
      )}
      {!isLoading && !isError && projects?.length === 0 && (
        <div className={ui.emptyState}>
          <div className={ui.emptyIcon}>
            <Icon name="folder" size={24} />
          </div>
          <h4>{t('projects.empty')}</h4>
          <p>{t('projects.emptyHint')}</p>
          <Link to="/projects/new" className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}>
            {t('projects.new')}
          </Link>
        </div>
      )}
      {!isLoading && !isError && !!projects?.length && (
        <div className={ui.tableWrap}>
          <table className={ui.table}>
            <thead>
              <tr>
                <th>{t('projects.columns.name')}</th>
                <th>{t('projects.columns.status')}</th>
                <th className={ui.num}>{t('projects.columns.opening')}</th>
                <th className={ui.num}>{t('projects.columns.target')}</th>
                <th>{t('projects.columns.period')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {projects.map((project) => (
                <tr key={project.id}>
                  <td>
                    <div className={ui.cellMain}>{project.name}</div>
                    {project.recurringSeriesId && (
                      <div className={ui.cellSub}>
                        <Icon name="repeat" size={11} /> {project.recurringSeriesId}
                      </div>
                    )}
                  </td>
                  <td>
                    <ProjectStatusPill status={project.status} />
                  </td>
                  <td className={ui.num}>{formatFcfa(project.openingBalance)}</td>
                  <td className={ui.num}>
                    {project.targetAmount ? formatFcfa(project.targetAmount) : t('projects.noTarget')}
                  </td>
                  <td className={ui.cellSub}>
                    {project.startDate ? formatDate(project.startDate) : t('projects.noPeriod')}
                  </td>
                  <td>
                    <Link
                      to={`/projects/${project.id}`}
                      className={ui.iconBtn}
                      aria-label={t('projects.viewAction')}
                      title={t('projects.viewAction')}
                    >
                      <Icon name="eye" size={14} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export function ProjectsListPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<Tab>('projects')

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('projects.title')}</h1>
        </div>
        {tab === 'projects' && (
          <div className={ui.actions}>
            <Link to="/projects/new" className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}>
              <Icon name="plus" strokeWidth={2.4} />
              {t('projects.new')}
            </Link>
          </div>
        )}
      </header>

      <div className={ui.seg} role="tablist" aria-label={t('projects.title')}>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'projects'}
          className={tab === 'projects' ? ui.segActive : undefined}
          onClick={() => setTab('projects')}
        >
          {t('projects.tabs.projects')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'categories'}
          className={tab === 'categories' ? ui.segActive : undefined}
          onClick={() => setTab('categories')}
        >
          {t('projects.tabs.categories')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'activities'}
          className={tab === 'activities' ? ui.segActive : undefined}
          onClick={() => setTab('activities')}
        >
          {t('projects.tabs.activities')}
        </button>
      </div>

      <div className={styles.tabPanel}>
        {tab === 'projects' && <ProjectsTable />}
        {tab === 'categories' && <CategoriesPanel />}
        {tab === 'activities' && <ActivitiesPanel />}
      </div>
    </section>
  )
}
