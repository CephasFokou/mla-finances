import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { mediaFileUrl } from '../../features/media/api'
import {
  useActivateMediaMutation,
  useDeactivateMediaMutation,
  useProjectVisualsQuery,
} from '../../features/media/hooks'
import { useProjectsQuery } from '../../features/reference-data/hooks'
import { formatDateTime } from '../../lib/format'
import styles from './MediaPage.module.scss'

export function ProjectVisualsPanel() {
  const { t } = useTranslation()
  const { data: allProjects } = useProjectsQuery()
  // Un visuel n'a de sens que pour un projet encore en cours de collecte (cf. décision du
  // module Médias) — même filtre que le formulaire de demande de retrait.
  const projects = allProjects?.filter((project) => project.status === 'ACTIVE')
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>(undefined)
  const [includeInactive, setIncludeInactive] = useState(false)
  const projectId = selectedProjectId ?? projects?.[0]?.id
  const { data: visuals, isLoading, isError } = useProjectVisualsQuery(projectId, includeInactive)
  const activateMutation = useActivateMediaMutation()
  const deactivateMutation = useDeactivateMediaMutation()

  return (
    <div>
      <div className={styles.projectSelectRow}>
        <label className={ui.field}>
          {t('media.visuals.project')}
          <select value={projectId ?? ''} onChange={(e) => setSelectedProjectId(Number(e.target.value))}>
            {projects?.map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
          </select>
        </label>
        <label className={ui.checkRow}>
          <input
            type="checkbox"
            checked={includeInactive}
            onChange={(e) => setIncludeInactive(e.target.checked)}
          />
          {t('media.banners.showInactive')}
        </label>
      </div>

      <div className={`${ui.actions} ${styles.panelActionsEnd}`}>
        <Link to="/media/visuals/new" className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}>
          <Icon name="plus" strokeWidth={2.4} />
          {t('media.visuals.new')}
        </Link>
      </div>

      {!projectId && (
        <div className={ui.emptyState}>
          <div className={ui.emptyIcon}>
            <Icon name="folder" size={24} />
          </div>
          <h4>{t('media.visuals.noProjects')}</h4>
        </div>
      )}

      {projectId && isLoading && (
        <div className={styles.skeletons}>
          <div className={ui.skel} />
          <div className={ui.skel} />
        </div>
      )}
      {projectId && isError && (
        <div className={ui.emptyState}>
          <div className={ui.emptyIcon}>
            <Icon name="alert" size={24} />
          </div>
          <h4>{t('media.error')}</h4>
        </div>
      )}
      {projectId && !isLoading && !isError && visuals?.length === 0 && (
        <div className={ui.emptyState}>
          <div className={ui.emptyIcon}>
            <Icon name="image" size={24} />
          </div>
          <h4>{t('media.visuals.empty')}</h4>
        </div>
      )}
      {projectId && !isLoading && !isError && !!visuals?.length && (
        <div className={styles.grid}>
          {visuals.map((visual) => (
            <div key={visual.id} className={styles.card}>
              <div className={styles.thumbWrap}>
                <img
                  src={mediaFileUrl(visual.id)}
                  alt={visual.title ?? visual.fileName}
                  className={styles.thumb}
                />
              </div>
              <div className={styles.cardBody}>
                <span className={styles.cardTitle}>{visual.title ?? visual.fileName}</span>
                <span className={styles.cardMeta}>{formatDateTime(visual.uploadedAt)}</span>
              </div>
              <div className={styles.cardFoot}>
                <span className={`${ui.pill} ${visual.active ? ui.pillGreen : ui.pillGrey}`}>
                  {visual.active ? t('media.active') : t('media.inactive')}
                </span>
                <button
                  type="button"
                  className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                  disabled={activateMutation.isPending || deactivateMutation.isPending}
                  onClick={() =>
                    void (visual.active
                      ? deactivateMutation.mutateAsync(visual.id)
                      : activateMutation.mutateAsync(visual.id))
                  }
                >
                  {visual.active ? t('media.deactivate') : t('media.activate')}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
