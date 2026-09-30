import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import ui from '../../components/ui/ui.module.scss'
import { RolesPanel } from './RolesPanel'
import styles from './AdministrationPage.module.scss'
import { UsersPanel } from './UsersPanel'

type Tab = 'users' | 'roles'

export function AdministrationPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<Tab>('users')

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('administration.title')}</h1>
        </div>
      </header>

      <div className={ui.seg} role="tablist" aria-label={t('administration.title')}>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'users'}
          className={tab === 'users' ? ui.segActive : undefined}
          onClick={() => setTab('users')}
        >
          {t('administration.tabs.users')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'roles'}
          className={tab === 'roles' ? ui.segActive : undefined}
          onClick={() => setTab('roles')}
        >
          {t('administration.tabs.roles')}
        </button>
      </div>

      <div className={styles.tabPanel}>{tab === 'users' ? <UsersPanel /> : <RolesPanel />}</div>
    </section>
  )
}
