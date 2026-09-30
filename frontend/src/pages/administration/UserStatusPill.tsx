import { useTranslation } from 'react-i18next'

import ui from '../../components/ui/ui.module.scss'
import type { UserAccountStatus } from '../../features/administration/types'

const VARIANTS: Record<UserAccountStatus, string | undefined> = {
  PENDING_INVITATION: ui.pillAmber,
  ACTIVE: ui.pillGreen,
  SUSPENDED: ui.pillRed,
}

export function UserStatusPill({ status }: { status: UserAccountStatus }) {
  const { t } = useTranslation()
  return (
    <span className={`${ui.pill} ${VARIANTS[status]}`}>{t(`administration.users.status.${status}`)}</span>
  )
}
