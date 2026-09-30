import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import styles from './AuthPage.module.scss'

/** RG-18 : affichée par RequirePermission (voir app/guards.tsx) quand l'utilisateur connecté n'a
 *  pas la permission requise pour la section demandée. Rappel : c'est un confort côté client, la
 *  protection réelle reste @PreAuthorize côté backend. */
export function UnauthorizedPage() {
  const { t } = useTranslation()

  return (
    <main className={styles.page}>
      <div className={`${styles.card} ${styles.center}`}>
        <div className={styles.crest}>
          <Icon name="alert" size={20} />
        </div>
        <div>
          <h1>{t('auth.unauthorized.title')}</h1>
          <p className={styles.lead}>{t('auth.unauthorized.lead')}</p>
        </div>
        <Link to="/" className={`${ui.btn} ${ui.btnPrimary} ${styles.submit}`}>
          {t('auth.unauthorized.backToDashboard')}
        </Link>
      </div>
    </main>
  )
}
