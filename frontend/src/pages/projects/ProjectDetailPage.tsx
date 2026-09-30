import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useProjectBalanceQuery } from '../../features/contributions/hooks'
import {
  useActivateProjectMutation,
  useArchiveProjectMutation,
  useCompleteProjectMutation,
  useCreateOccurrenceMutation,
  useProjectOccurrencesQuery,
  useProjectQuery,
  useUpdateProjectMutation,
} from '../../features/projects/hooks'
import type { ProjectOccurrenceInput } from '../../features/projects/types'
import { ApiError } from '../../lib/apiClient'
import { formatDate, formatDateTime, formatFcfa } from '../../lib/format'
import { ProjectStatusPill } from './ProjectStatusPill'
import styles from './ProjectDetailPage.module.scss'

type LifecycleAction = 'activate' | 'complete' | 'archive'

function NewOccurrenceForm({
  seriesId,
  sourceId,
  onDone,
}: {
  seriesId: string
  sourceId: number
  onDone: () => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [openingBalance, setOpeningBalance] = useState('0')
  const [targetAmount, setTargetAmount] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const createOccurrence = useCreateOccurrenceMutation(sourceId, seriesId)

  const submit = async () => {
    setError(null)
    const input: ProjectOccurrenceInput = {
      name,
      openingBalance: Number(openingBalance || 0),
      targetAmount: targetAmount ? Number(targetAmount) : undefined,
      startDate: startDate || undefined,
      endDate: endDate || undefined,
    }
    try {
      await createOccurrence.mutateAsync(input)
      onDone()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('projects.form.genericError'))
    }
  }

  return (
    <div className={styles.occurrenceForm}>
      <h4>{t('projects.detail.occurrenceForm.title', { series: seriesId })}</h4>
      <div className={ui.formGrid}>
        <label className={`${ui.field} ${ui.span2}`}>
          {t('projects.detail.occurrenceForm.name')}
          <input value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label className={ui.field}>
          {t('projects.detail.occurrenceForm.openingBalance')}
          <input
            type="number"
            min="0"
            value={openingBalance}
            onChange={(e) => setOpeningBalance(e.target.value)}
          />
        </label>
        <label className={ui.field}>
          {t('projects.detail.occurrenceForm.targetAmount')}
          <input
            type="number"
            min="0"
            value={targetAmount}
            onChange={(e) => setTargetAmount(e.target.value)}
          />
        </label>
        <label className={ui.field}>
          {t('projects.detail.occurrenceForm.startDate')}
          <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        </label>
        <label className={ui.field}>
          {t('projects.detail.occurrenceForm.endDate')}
          <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
        </label>
      </div>
      {error && <span className={ui.error}>{error}</span>}
      <div className={`${ui.actions} ${styles.right}`}>
        <button type="button" className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`} onClick={onDone}>
          {t('projects.detail.occurrenceForm.cancel')}
        </button>
        <button
          type="button"
          className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
          disabled={!name.trim() || createOccurrence.isPending}
          onClick={() => void submit()}
        >
          {t('projects.detail.occurrenceForm.submit')}
        </button>
      </div>
    </div>
  )
}

function OccurrencesCard({ project }: { project: { id: number; recurringSeriesId: string | null } }) {
  const { t } = useTranslation()
  const [isCreating, setIsCreating] = useState(false)
  const { data: occurrences } = useProjectOccurrencesQuery(project.recurringSeriesId)

  if (!project.recurringSeriesId) {
    return null
  }
  const siblings = occurrences?.filter((o) => o.id !== project.id) ?? []

  return (
    <div className={ui.card}>
      <div className={ui.cardHead}>
        <h3>{t('projects.detail.occurrencesTitle')}</h3>
        <span className={ui.sub}>{project.recurringSeriesId}</span>
      </div>

      {siblings.length === 0 && !isCreating && (
        <p className={ui.sub}>{t('projects.detail.occurrencesEmpty')}</p>
      )}

      {siblings.length > 0 && (
        <ul className={styles.occurrenceList}>
          {siblings.map((occurrence) => (
            <li key={occurrence.id} className={styles.occurrenceRow}>
              <Link to={`/projects/${occurrence.id}`}>{occurrence.name}</Link>
              <ProjectStatusPill status={occurrence.status} />
            </li>
          ))}
        </ul>
      )}

      {!isCreating ? (
        <button
          type="button"
          className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
          onClick={() => setIsCreating(true)}
        >
          <Icon name="plus" strokeWidth={2.4} />
          {t('projects.detail.newOccurrence')}
        </button>
      ) : (
        <NewOccurrenceForm
          seriesId={project.recurringSeriesId}
          sourceId={project.id}
          onDone={() => setIsCreating(false)}
        />
      )}
    </div>
  )
}

export function ProjectDetailPage() {
  const { t } = useTranslation()
  const params = useParams<{ id: string }>()
  const id = Number(params.id)
  const { data: project, isLoading, isError } = useProjectQuery(id)
  const balance = useProjectBalanceQuery(project ? project.id : undefined)

  const updateMutation = useUpdateProjectMutation(id)
  const activateMutation = useActivateProjectMutation(id)
  const completeMutation = useCompleteProjectMutation(id)
  const archiveMutation = useArchiveProjectMutation(id)

  const [isEditing, setIsEditing] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [targetAmount, setTargetAmount] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [editError, setEditError] = useState<string | null>(null)

  const [pendingAction, setPendingAction] = useState<LifecycleAction | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  if (isLoading) {
    return (
      <div className={styles.skeletons} role="status" aria-label={t('projects.loading')}>
        <div className={ui.skel} />
        <div className={ui.skel} />
        <div className={ui.skel} />
      </div>
    )
  }
  if (isError || !project) {
    return (
      <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
        <Icon name="alert" size={17} />
        <div>{t('projects.error')}</div>
      </div>
    )
  }

  const startEditing = () => {
    setName(project.name)
    setDescription(project.description ?? '')
    setTargetAmount(project.targetAmount != null ? String(project.targetAmount) : '')
    setStartDate(project.startDate ?? '')
    setEndDate(project.endDate ?? '')
    setEditError(null)
    setIsEditing(true)
  }

  const saveEdit = async () => {
    setEditError(null)
    try {
      await updateMutation.mutateAsync({
        name,
        description: description || undefined,
        targetAmount: targetAmount ? Number(targetAmount) : undefined,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      })
      setIsEditing(false)
    } catch (error) {
      setEditError(error instanceof ApiError ? error.message : t('projects.form.genericError'))
    }
  }

  const runPendingAction = async () => {
    setActionError(null)
    try {
      if (pendingAction === 'activate') await activateMutation.mutateAsync()
      if (pendingAction === 'complete') await completeMutation.mutateAsync()
      if (pendingAction === 'archive') await archiveMutation.mutateAsync()
      setPendingAction(null)
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : t('projects.detail.actionError'))
    }
  }

  const isArchived = project.status === 'ARCHIVED'
  const targetReached =
    project.targetAmount != null &&
    (balance.data?.currentBalance ?? project.openingBalance) >= project.targetAmount

  return (
    <section>
      <Link to="/projects" className={ui.backLink}>
        <Icon name="arrowLeft" size={15} />
        {t('projects.detail.back')}
      </Link>

      <header className={ui.pageHead}>
        <div>
          <h1>{project.name}</h1>
          <p>{t('projects.detail.createdAt', { date: formatDateTime(project.createdAt) })}</p>
        </div>
        {!isEditing && (
          <div className={ui.actions}>
            <ProjectStatusPill status={project.status} />
            {!isArchived && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                onClick={startEditing}
              >
                <Icon name="edit" />
                {t('projects.detail.edit')}
              </button>
            )}
            {project.status === 'DRAFT' && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
                onClick={() => setPendingAction('activate')}
              >
                <Icon name="check" strokeWidth={2.4} />
                {t('projects.detail.activate')}
              </button>
            )}
            {project.status === 'ACTIVE' && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                onClick={() => setPendingAction('complete')}
              >
                <Icon name="check" strokeWidth={2.4} />
                {t('projects.detail.complete')}
              </button>
            )}
            {!isArchived && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnDanger} ${ui.btnSm}`}
                onClick={() => setPendingAction('archive')}
              >
                <Icon name="x" strokeWidth={2.4} />
                {t('projects.detail.archive')}
              </button>
            )}
          </div>
        )}
      </header>

      <div className={styles.grid}>
        <div className={ui.card}>
          <div className={ui.cardHead}>
            <h3>{t('projects.detail.general')}</h3>
          </div>
          <table className={styles.infoTable}>
            <tbody>
              <tr>
                <th scope="row">{t('projects.detail.description')}</th>
                <td>{project.description || '—'}</td>
              </tr>
              <tr>
                <th scope="row">{t('projects.detail.openingBalance')}</th>
                <td className={ui.cellMain}>{formatFcfa(project.openingBalance)}</td>
              </tr>
              <tr>
                <th scope="row">{t('projects.detail.targetAmount')}</th>
                <td>
                  {project.targetAmount ? formatFcfa(project.targetAmount) : t('projects.noTarget')}
                  {targetReached && (
                    <span className={`${ui.pill} ${ui.pillGreen}`} style={{ marginLeft: 8 }}>
                      {t('projects.detail.targetReached')}
                    </span>
                  )}
                </td>
              </tr>
              <tr>
                <th scope="row">{t('projects.detail.period')}</th>
                <td>
                  {project.startDate ? formatDate(project.startDate) : t('projects.noPeriod')}
                  {project.endDate ? ` — ${formatDate(project.endDate)}` : ''}
                </td>
              </tr>
              {project.recurringSeriesId && (
                <tr>
                  <th scope="row">{t('projects.detail.recurringSeriesId')}</th>
                  <td>{project.recurringSeriesId}</td>
                </tr>
              )}
            </tbody>
          </table>

          {isEditing && (
            <div className={styles.editBlock}>
              <div className={ui.formGrid}>
                <label className={`${ui.field} ${ui.span2}`}>
                  {t('projects.form.name')}
                  <input value={name} onChange={(e) => setName(e.target.value)} />
                </label>
                <label className={`${ui.field} ${ui.span2}`}>
                  {t('projects.form.description')}
                  <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
                </label>
                <label className={ui.field}>
                  {t('projects.form.targetAmount')}
                  <input
                    type="number"
                    min="0"
                    value={targetAmount}
                    onChange={(e) => setTargetAmount(e.target.value)}
                  />
                </label>
                <label className={ui.field}>
                  {t('projects.form.startDate')}
                  <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
                </label>
                <label className={ui.field}>
                  {t('projects.form.endDate')}
                  <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
                </label>
              </div>
              {editError && <span className={ui.error}>{editError}</span>}
              <div className={`${ui.actions} ${styles.right}`}>
                <button
                  type="button"
                  className={`${ui.btn} ${ui.btnSecondary}`}
                  onClick={() => setIsEditing(false)}
                >
                  {t('projects.detail.cancelEdit')}
                </button>
                <button
                  type="button"
                  className={`${ui.btn} ${ui.btnPrimary}`}
                  disabled={!name.trim() || updateMutation.isPending}
                  onClick={() => void saveEdit()}
                >
                  {t('projects.detail.saveEdit')}
                </button>
              </div>
            </div>
          )}
        </div>

        <div className={styles.side}>
          {balance.data && (
            <div className={ui.card}>
              <div className={ui.cardHead}>
                <h3>{t('projects.detail.balanceTitle')}</h3>
              </div>
              <div className={styles.balanceTile}>
                <div className={styles.balanceLabel}>{t('projects.detail.currentBalance')}</div>
                <div className={styles.balanceValue}>{formatFcfa(balance.data.currentBalance)}</div>
                <div className={ui.sub}>
                  {t('projects.detail.balanceCount', { count: balance.data.contributionCount })}
                </div>
              </div>
            </div>
          )}

          <OccurrencesCard project={project} />
        </div>
      </div>

      {pendingAction && (
        <div className={styles.overlay} onClick={() => setPendingAction(null)}>
          <div
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="lifecycle-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className={styles.modalHead}>
              <span className={styles.modalIcon}>
                <Icon name={pendingAction === 'archive' ? 'x' : 'check'} size={18} />
              </span>
              <h3 id="lifecycle-title">{t(`projects.detail.${pendingAction}`)}</h3>
            </div>
            <div className={styles.modalBody}>
              <p>{t(`projects.detail.${pendingAction}Confirm`)}</p>
              {actionError && <span className={ui.error}>{actionError}</span>}
            </div>
            <div className={styles.modalFoot}>
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary}`}
                onClick={() => setPendingAction(null)}
              >
                {t('projects.detail.cancelEdit')}
              </button>
              <button
                type="button"
                className={`${ui.btn} ${pendingAction === 'archive' ? ui.btnDanger : ui.btnPrimary}`}
                onClick={() => void runPendingAction()}
              >
                {t(`projects.detail.${pendingAction}`)}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
