import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'

import { useAuthStore } from '../../store/authStore'
import { Icon, type IconName } from '../ui/Icon'
import styles from './AppLayout.module.scss'

interface NavItem {
  to: string
  label: string
  icon: IconName
}

/**
 * Coquille de l'application back-office (barre latérale + barre supérieure + contenu), reprise des
 * maquettes Stitch. Le méta-bandeau horizontal des maquettes n'en fait pas partie : c'était un outil
 * de revue.
 */
export function AppLayout() {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const currentUser = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const navItems: NavItem[] = [
    { to: '/', label: t('nav.dashboard'), icon: 'grid' },
    { to: '/contributions', label: t('nav.contributions'), icon: 'coins' },
    { to: '/projects', label: t('nav.projects'), icon: 'folder' },
    { to: '/withdrawals', label: t('nav.withdrawals'), icon: 'out' },
    { to: '/reports', label: t('nav.reports'), icon: 'file' },
    { to: '/payment-transactions', label: t('nav.paymentTransactions'), icon: 'heart' },
    { to: '/administration', label: t('nav.administration'), icon: 'gear' },
    { to: '/media', label: t('nav.media'), icon: 'image' },
  ]

  // Le titre de la barre supérieure suit la section courante (la plus longue correspondance).
  const currentLabel =
    [...navItems]
      .reverse()
      .find((item) => (item.to === '/' ? pathname === '/' : pathname.startsWith(item.to)))?.label ??
    t('nav.dashboard')

  return (
    <div className={styles.shell}>
      <aside className={`${styles.sidebar} ${menuOpen ? styles.sidebarOpen : ''}`}>
        <div className={styles.brand}>
          <div className={styles.crest}>
            <Icon name="crest" size={20} />
          </div>
          <div>
            <b>{t('app.name')}</b>
            <span>{t('app.church')}</span>
          </div>
        </div>
        <nav className={styles.nav}>
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              onClick={() => setMenuOpen(false)}
              className={({ isActive }) => (isActive ? `${styles.navLink} ${styles.active}` : styles.navLink)}
            >
              <Icon name={item.icon} size={17} />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className={styles.main}>
        <header className={styles.topbar}>
          <button
            type="button"
            className={styles.hamburger}
            aria-label={t('nav.toggleMenu')}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <Icon name="menu" size={18} />
          </button>
          <div>
            <div className={styles.crumb}>
              {t('app.name')} / {currentLabel}
            </div>
            <h2 className={styles.title}>{currentLabel}</h2>
          </div>
          <div className={styles.account}>
            {currentUser && <span className={styles.accountName}>{currentUser.fullName}</span>}
            <button type="button" className={styles.logout} onClick={handleLogout}>
              <Icon name="out" size={15} />
              {t('nav.logout')}
            </button>
          </div>
        </header>
        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
