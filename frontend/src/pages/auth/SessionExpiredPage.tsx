import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import styles from './AuthPage.module.scss'

/** Affichée quand apiClient reçoit un 401 alors qu'un jeton était attaché (voir authStore.expireSession) —
 *  distincte de /login pour ne pas laisser croire à un mot de passe incorrect. */
export function SessionExpiredPage() {
  const { t } = useTranslation()

  return (
    <main className={styles.page}>
      <div className={`${styles.card} ${styles.center}`}>
        <div className={styles.crest}>
          <Icon name="clock" size={20} />
        </div>
        <div>
          <h1>{t('auth.sessionExpired.title')}</h1>
          <p className={styles.lead}>{t('auth.sessionExpired.lead')}</p>
        </div>
        <Link to="/login" className={`${ui.btn} ${ui.btnPrimary} ${styles.submit}`}>
          {t('auth.sessionExpired.backToLogin')}
        </Link>
      </div>
    </main>
  )
}
