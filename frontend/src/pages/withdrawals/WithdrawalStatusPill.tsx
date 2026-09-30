import { useTranslation } from 'react-i18next'

import ui from '../../components/ui/ui.module.scss'
import type { WithdrawalStatus } from '../../features/withdrawals/types'

const VARIANTS: Record<WithdrawalStatus, string | undefined> = {
  PENDING: ui.pillAmber,
  APPROVED: ui.pillNavy,
  REJECTED: ui.pillRed,
  PROCESSED: ui.pillGreen,
  CANCELLED: ui.pillGrey,
}

export function WithdrawalStatusPill({ status }: { status: WithdrawalStatus }) {
  const { t } = useTranslation()
  return <span className={`${ui.pill} ${VARIANTS[status]}`}>{t(`withdrawals.status.${status}`)}</span>
}
