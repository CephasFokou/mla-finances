import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import ui from '../../components/ui/ui.module.scss'
import { BannersPanel } from './BannersPanel'
import { MediaLibraryPanel } from './MediaLibraryPanel'
import styles from './MediaPage.module.scss'
import { ProjectVisualsPanel } from './ProjectVisualsPanel'

type Tab = 'banners' | 'visuals' | 'library'

export function MediaPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<Tab>('banners')

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('media.title')}</h1>
        </div>
      </header>

      <div className={ui.seg} role="tablist" aria-label={t('media.title')}>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'banners'}
          className={tab === 'banners' ? ui.segActive : undefined}
          onClick={() => setTab('banners')}
        >
          {t('media.tabs.banners')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'visuals'}
          className={tab === 'visuals' ? ui.segActive : undefined}
          onClick={() => setTab('visuals')}
        >
          {t('media.tabs.visuals')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'library'}
          className={tab === 'library' ? ui.segActive : undefined}
          onClick={() => setTab('library')}
        >
          {t('media.tabs.library')}
        </button>
      </div>

      <div className={styles.tabPanel}>
        {tab === 'banners' && <BannersPanel />}
        {tab === 'visuals' && <ProjectVisualsPanel />}
        {tab === 'library' && <MediaLibraryPanel />}
      </div>
    </section>
  )
}
