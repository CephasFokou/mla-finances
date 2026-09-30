import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { mediaFileUrl } from '../../features/media/api'
import {
  useActivateMediaMutation,
  useDeactivateMediaMutation,
  useMediaQuery,
  useUploadMediaMutation,
} from '../../features/media/hooks'
import { ApiError } from '../../lib/apiClient'
import { formatDateTime } from '../../lib/format'
import styles from './MediaPage.module.scss'

function UploadLibraryModal({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation()
  const uploadMutation = useUploadMediaMutation()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)

  const pickFile = (selected: File | null) => {
    setFile(selected)
    setPreviewUrl(selected ? URL.createObjectURL(selected) : null)
  }

  const save = async () => {
    setError(null)
    if (!file) {
      setError(t('media.form.fileRequired'))
      return
    }
    try {
      await uploadMutation.mutateAsync({ category: 'LIBRARY', file, title: title || undefined })
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('media.genericError'))
    }
  }

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="upload-media-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.modalHead}>
          <span className={styles.modalIcon}>
            <Icon name="image" size={18} />
          </span>
          <h3 id="upload-media-title">{t('media.library.uploadTitle')}</h3>
        </div>
        <div className={styles.modalBody}>
          <div className={styles.dropzone} onClick={() => fileInputRef.current?.click()}>
            {previewUrl ? (
              <img src={previewUrl} alt="" className={styles.preview} />
            ) : (
              <>
                <Icon name="image" size={28} />
                <span>{t('media.form.chooseFile')}</span>
              </>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
            />
          </div>
          <label className={ui.field}>
            {t('media.form.title')}
            <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} />
          </label>
          {error && <span className={ui.error}>{error}</span>}
        </div>
        <div className={styles.modalFoot}>
          <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={onClose}>
            {t('media.form.cancel')}
          </button>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnPrimary}`}
            disabled={uploadMutation.isPending}
            onClick={() => void save()}
          >
            {t('media.library.upload')}
          </button>
        </div>
      </div>
    </div>
  )
}

export function MediaLibraryPanel() {
  const { t } = useTranslation()
  const [includeInactive, setIncludeInactive] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [copiedId, setCopiedId] = useState<number | null>(null)
  const { data: items, isLoading, isError } = useMediaQuery('LIBRARY', includeInactive)
  const activateMutation = useActivateMediaMutation()
  const deactivateMutation = useDeactivateMediaMutation()

  const copyLink = (id: number) => {
    void navigator.clipboard.writeText(mediaFileUrl(id)).then(() => {
      setCopiedId(id)
      setTimeout(() => setCopiedId(null), 1500)
    })
  }

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
        <button
          type="button"
          className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
          onClick={() => setUploading(true)}
        >
          <Icon name="plus" strokeWidth={2.4} />
          {t('media.library.new')}
        </button>
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
      {!isLoading && !isError && items?.length === 0 && (
        <div className={ui.emptyState}>
          <div className={ui.emptyIcon}>
            <Icon name="image" size={24} />
          </div>
          <h4>{t('media.library.empty')}</h4>
        </div>
      )}
      {!isLoading && !isError && !!items?.length && (
        <div className={styles.grid}>
          {items.map((item) => (
            <div key={item.id} className={styles.card}>
              <div className={styles.thumbWrap}>
                <img src={mediaFileUrl(item.id)} alt={item.title ?? item.fileName} className={styles.thumb} />
              </div>
              <div className={styles.cardBody}>
                <span className={styles.cardTitle}>{item.title ?? item.fileName}</span>
                <span className={styles.cardMeta}>{formatDateTime(item.uploadedAt)}</span>
              </div>
              <div className={styles.cardFoot}>
                <button
                  type="button"
                  className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                  onClick={() => copyLink(item.id)}
                >
                  {copiedId === item.id ? t('media.library.copied') : t('media.library.copyLink')}
                </button>
                <button
                  type="button"
                  className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                  disabled={activateMutation.isPending || deactivateMutation.isPending}
                  onClick={() =>
                    void (item.active
                      ? deactivateMutation.mutateAsync(item.id)
                      : activateMutation.mutateAsync(item.id))
                  }
                >
                  {item.active ? t('media.deactivate') : t('media.activate')}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {uploading && <UploadLibraryModal onClose={() => setUploading(false)} />}
    </div>
  )
}
