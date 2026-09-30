import { useTranslation } from 'react-i18next'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import type { MovementType } from '../../features/contributions/types'

const VARIANTS: Record<MovementType, string | undefined> = {
  STANDARD: ui.pillGreen,
  ADJUSTMENT: ui.pillAmber,
  TRANSFER: ui.pillNavy,
}

/** Pastille du type de mouvement — le backend n'expose pas d'autre statut de contribution. */
export function MovementPill({ type }: { type: MovementType }) {
  const { t } = useTranslation()
  return (
    <span className={`${ui.pill} ${VARIANTS[type]}`}>
      {type === 'STANDARD' && <Icon name="check" size={11} strokeWidth={2.4} />}
      {t(`contributions.movement.${type}`)}
    </span>
  )
}
