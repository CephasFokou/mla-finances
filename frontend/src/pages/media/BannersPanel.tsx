import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { mediaFileUrl } from '../../features/media/api'
import {
  useActivateMediaMutation,
  useDeactivateMediaMutation,
  useMediaQuery,
} from '../../features/media/hooks'
import { formatDateTime } from '../../lib/format'
import styles from './MediaPage.module.scss'

export function BannersPanel() {
  const { t } = useTranslation()
  const [includeInactive, setIncludeInactive] = useState(false)
  const { data: banners, isLoading, isError } = useMediaQuery('BANNER', includeInactive)
  const activateMutation = useActivateMediaMutation()
  const deactivateMutation = useDeactivateMediaMutation()

  return (
    <div>
      <div className={`${ui.actions} ${styles.panelActions}`}>
        <label className={ui.checkRow}>
          <input
            type="checkbox"
            checked={includeInactive}
            onChange={(e) => setIncludeInactive(e.target.checked)}
          />
          {t('media.banners.showInactive')}
        </label>
        <div className={ui.actions}>
          <Link to="/media/banners/order" className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}>
            <Icon name="repeat" size={14} />
            {t('media.banners.order')}
          </Link>
          <Link to="/media/banners/new" className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}>
            <Icon name="plus" strokeWidth={2.4} />
            {t('media.banners.new')}
          </Link>
        </div>
      </div>

      {isLoading && (
        <div className={styles.skeletons}>
          <div className={ui.skel} />
          <div className={ui.skel} />
        </div>
      )}
      {isError && (
        <div className={ui.emptyState}>
          <div className={ui.emptyIcon}>
            <Icon name="alert" size={24} />
          </div>
          <h4>{t('media.error')}</h4>
        </div>
      )}
      {!isLoading && !isError && banners?.length === 0 && (
        <div className={ui.emptyState}>
          <div className={ui.emptyIcon}>
            <Icon name="image" size={24} />
          </div>
          <h4>{t('media.banners.empty')}</h4>
        </div>
      )}
      {!isLoading && !isError && !!banners?.length && (
        <div className={styles.grid}>
          {banners.map((banner) => (
            <div key={banner.id} className={styles.card}>
              <div className={styles.thumbWrap}>
                <img
                  src={mediaFileUrl(banner.id)}
                  alt={banner.title ?? banner.fileName}
                  className={styles.thumb}
                />
              </div>
              <div className={styles.cardBody}>
                <span className={styles.cardTitle}>{banner.title ?? banner.fileName}</span>
                <span className={styles.cardMeta}>{formatDateTime(banner.uploadedAt)}</span>
              </div>
              <div className={styles.cardFoot}>
                <span className={`${ui.pill} ${banner.active ? ui.pillGreen : ui.pillGrey}`}>
                  {banner.active ? t('media.active') : t('media.inactive')}
                </span>
                <button
                  type="button"
                  className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                  disabled={activateMutation.isPending || deactivateMutation.isPending}
                  onClick={() =>
                    void (banner.active
                      ? deactivateMutation.mutateAsync(banner.id)
                      : activateMutation.mutateAsync(banner.id))
                  }
                >
                  {banner.active ? t('media.deactivate') : t('media.activate')}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
