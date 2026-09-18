import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router-dom'

import styles from './AppLayout.module.scss'

/**
 * Coquille de l'application back-office (barre latérale + zone de contenu), reprise de la
 * structure de navigation des maquettes HTML/CSS validées. Les sections listées ici sont celles
 * réellement prévues côté produit (le méta-bandeau horizontal des maquettes n'en faisait pas partie
 * — c'était un outil de revue).
 */
export function AppLayout() {
  const { t } = useTranslation()

  const navItems = [
    { to: '/', label: t('nav.dashboard') },
    { to: '/contributions', label: t('nav.contributions') },
    { to: '/projects', label: t('nav.projects') },
    { to: '/withdrawals', label: t('nav.withdrawals') },
    { to: '/reports', label: t('nav.reports') },
    { to: '/administration', label: t('nav.administration') },
    { to: '/media', label: t('nav.media') },
  ]

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <div className={styles.brand}>{t('app.name')}</div>
        <nav className={styles.nav}>
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => (isActive ? `${styles.navLink} ${styles.active}` : styles.navLink)}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  )
}
