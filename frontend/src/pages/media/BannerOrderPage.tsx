import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { mediaFileUrl } from '../../features/media/api'
import { useMediaQuery, useReorderBannersMutation } from '../../features/media/hooks'
import { ApiError } from '../../lib/apiClient'
import styles from './MediaPage.module.scss'

// Réordonne l'ensemble des bannières (actives ET désactivées) : displayOrder est conservé même
// pour une bannière désactivée, pour qu'elle retrouve sa place si elle est réactivée plus tard.
// L'ordre affiché vient directement de la requête (pas d'état local à resynchroniser) : chaque
// déplacement calcule le nouvel ordre à partir des données actuelles et l'envoie aussitôt au
// serveur, qui invalide la requête et renvoie la liste à jour.
export function BannerOrderPage() {
  const { t } = useTranslation()
  const { data: banners, isLoading } = useMediaQuery('BANNER', true)
  const reorderMutation = useReorderBannersMutation()
  const [error, setError] = useState<string | null>(null)

  const move = (index: number, direction: -1 | 1) => {
    if (!banners) return
    const target = index + direction
    if (target < 0 || target >= banners.length) return
    const next = [...banners]
    const [moved] = next.splice(index, 1)
    if (!moved) return
    next.splice(target, 0, moved)
    setError(null)
    reorderMutation.mutate(
      { orderedIds: next.map((b) => b.id) },
      { onError: (err) => setError(err instanceof ApiError ? err.message : t('media.genericError')) },
    )
  }

  return (
    <section>
      <Link to="/media" className={ui.backLink}>
        <Icon name="arrowLeft" size={15} />
        {t('media.back')}
      </Link>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('media.banners.orderTitle')}</h1>
          <p>{t('media.banners.orderSubtitle')}</p>
        </div>
      </header>

      {error && (
        <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
          <Icon name="alert" size={17} />
          <div>{error}</div>
        </div>
      )}

      {isLoading && (
        <div className={styles.skeletons}>
          <div className={ui.skel} />
          <div className={ui.skel} />
        </div>
      )}

      {!isLoading && banners?.length === 0 && (
        <div className={ui.emptyState}>
          <div className={ui.emptyIcon}>
            <Icon name="image" size={24} />
          </div>
          <h4>{t('media.banners.empty')}</h4>
        </div>
      )}

      {!isLoading && !!banners?.length && (
        <div className={styles.orderList}>
          {banners.map((banner, index) => (
            <div key={banner.id} className={styles.orderRow}>
              <img src={mediaFileUrl(banner.id)} alt="" className={styles.orderThumb} />
              <div className={styles.orderInfo}>
                <span className={styles.orderTitle}>{banner.title ?? banner.fileName}</span>
                {!banner.active && <span className={`${ui.pill} ${ui.pillGrey}`}>{t('media.inactive')}</span>}
              </div>
              <div className={styles.orderMoves}>
                <button
                  type="button"
                  className={ui.iconBtn}
                  disabled={index === 0 || reorderMutation.isPending}
                  onClick={() => move(index, -1)}
                  aria-label={t('media.banners.moveUp')}
                >
                  <Icon name="arrowUp" size={14} />
                </button>
                <button
                  type="button"
                  className={ui.iconBtn}
                  disabled={index === banners.length - 1 || reorderMutation.isPending}
                  onClick={() => move(index, 1)}
                  aria-label={t('media.banners.moveDown')}
                >
                  <Icon name="arrowDown" size={14} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
