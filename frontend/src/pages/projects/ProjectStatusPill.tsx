import { useTranslation } from 'react-i18next'

import ui from '../../components/ui/ui.module.scss'
import type { ProjectStatus } from '../../features/projects/types'

const VARIANTS: Record<ProjectStatus, string | undefined> = {
  DRAFT: ui.pillGrey,
  ACTIVE: ui.pillGreen,
  COMPLETED: ui.pillNavy,
  ARCHIVED: ui.pillGrey,
}

export function ProjectStatusPill({ status }: { status: ProjectStatus }) {
  const { t } = useTranslation()
  return <span className={`${ui.pill} ${VARIANTS[status]}`}>{t(`projects.status.${status}`)}</span>
}
