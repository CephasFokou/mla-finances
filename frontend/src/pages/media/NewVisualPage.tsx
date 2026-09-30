import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useUploadMediaMutation } from '../../features/media/hooks'
import { useProjectsQuery } from '../../features/reference-data/hooks'
import { ApiError } from '../../lib/apiClient'
import styles from './MediaPage.module.scss'

export function NewVisualPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: allProjects } = useProjectsQuery()
  // Un visuel n'a de sens que pour un projet encore en cours de collecte (cf. décision du
  // module Médias) — même filtre que le formulaire de demande de retrait.
  const projects = allProjects?.filter((project) => project.status === 'ACTIVE')
  const uploadMutation = useUploadMediaMutation()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [projectId, setProjectId] = useState<number | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)

  const selectedProjectId = projectId ?? projects?.[0]?.id

  const pickFile = (selected: File | null) => {
    setFile(selected)
    setPreviewUrl(selected ? URL.createObjectURL(selected) : null)
  }

  const onSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError(null)
    if (!file) {
      setError(t('media.form.fileRequired'))
      return
    }
    if (!selectedProjectId) {
      setError(t('media.visuals.projectRequired'))
      return
    }
    try {
      await uploadMutation.mutateAsync({
        category: 'PROJECT_VISUAL',
        file,
        title: title || undefined,
        projectId: selectedProjectId,
      })
      navigate('/media')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('media.genericError'))
    }
  }

  return (
    <section>
      <Link to="/media" className={ui.backLink}>
        <Icon name="arrowLeft" size={15} />
        {t('media.back')}
      </Link>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('media.visuals.newTitle')}</h1>
          <p>{t('media.visuals.newSubtitle')}</p>
        </div>
      </header>

      <form className={`${ui.card} ${styles.form}`} onSubmit={(e) => void onSubmit(e)} noValidate>
        <label className={ui.field}>
          {t('media.visuals.project')}
          <select value={selectedProjectId ?? ''} onChange={(e) => setProjectId(Number(e.target.value))}>
            {projects?.map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
          </select>
        </label>

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

        {error && (
          <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
            <Icon name="alert" size={17} />
            <div>{error}</div>
          </div>
        )}

        <div className={`${ui.actions} ${styles.formActions}`}>
          <Link to="/media" className={`${ui.btn} ${ui.btnSecondary}`}>
            {t('media.form.cancel')}
          </Link>
          <button type="submit" className={`${ui.btn} ${ui.btnPrimary}`} disabled={uploadMutation.isPending}>
            {t('media.visuals.publish')}
          </button>
        </div>
      </form>
    </section>
  )
}
