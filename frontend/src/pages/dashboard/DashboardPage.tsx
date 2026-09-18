import { useTranslation } from 'react-i18next'

import styles from './DashboardPage.module.scss'

export function DashboardPage() {
  const { t } = useTranslation()

  return (
    <section className={styles.page}>
      <h1>{t('dashboard.title')}</h1>
      <p className={styles.placeholder}>{t('dashboard.placeholder')}</p>
    </section>
  )
}
