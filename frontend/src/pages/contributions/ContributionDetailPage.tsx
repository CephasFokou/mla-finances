import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import {
  useCancelContributionMutation,
  useCategoryBalanceQuery,
  useContributionQuery,
  useProjectBalanceQuery,
  useUpdateContributionMutation,
} from '../../features/contributions/hooks'
import { ApiError } from '../../lib/apiClient'
import { formatDate, formatDateTime, formatFcfa, formatMonth, personLabel } from '../../lib/format'
import { MovementPill } from './MovementPill'
import styles from './ContributionDetailPage.module.scss'

export function ContributionDetailPage() {
  const { t } = useTranslation()
  const params = useParams<{ id: string }>()
  const id = Number(params.id)
  const { data: contribution, isLoading, isError } = useContributionQuery(id)

  const updateMutation = useUpdateContributionMutation(id)
  const cancelMutation = useCancelContributionMutation(id)
  const projectBalance = useProjectBalanceQuery(contribution?.project?.id)
  const categoryBalance = useCategoryBalanceQuery(contribution?.category?.id)
  const balance = contribution?.project ? projectBalance.data : categoryBalance.data

  const [isEditing, setIsEditing] = useState(false)
  const [notes, setNotes] = useState('')
  const [anonymous, setAnonymous] = useState(false)
  const [remark, setRemark] = useState('')

  const [isCancelling, setIsCancelling] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  if (isLoading) {
    return (
      <div className={styles.skeletons} role="status" aria-label={t('contributions.loading')}>
        <div className={ui.skel} />
        <div className={ui.skel} />
        <div className={ui.skel} />
      </div>
    )
  }
  if (isError || !contribution) {
    return (
      <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
        <Icon name="alert" size={17} />
        <div>{t('contributions.error')}</div>
      </div>
    )
  }

  const startEditing = () => {
    setNotes(contribution.notes ?? '')
    setAnonymous(contribution.anonymous)
    setRemark(contribution.remark ?? '')
    setActionError(null)
    setIsEditing(true)
  }

  const saveEdit = async () => {
    setActionError(null)
    try {
      // Le backend remplace tels quels les champs non financiers : on renvoie la personne et
      // l'activité actuelles, sinon une simple correction de note les effacerait.
      await updateMutation.mutateAsync({
        notes,
        anonymous,
        donorId: contribution.donor?.id ?? null,
        activityId: contribution.activity?.id ?? null,
        remark: remark || null,
      })
      setIsEditing(false)
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : t('contributions.form.genericError'))
    }
  }

  const closeCancelDialog = () => {
    setIsCancelling(false)
    setCancelReason('')
    setActionError(null)
  }

  const confirmCancel = async () => {
    setActionError(null)
    try {
      await cancelMutation.mutateAsync({ reason: cancelReason })
      closeCancelDialog()
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : t('contributions.form.genericError'))
    }
  }

  const isAdjustment = contribution.movementType === 'ADJUSTMENT'
  const isAlreadyCancelled = cancelMutation.isSuccess
  const canAct = !isAdjustment && !isAlreadyCancelled
  const envelopeName = contribution.project?.name ?? contribution.category?.name ?? '—'
  const donorName = contribution.anonymous
    ? t('contributions.anonymousLabel')
    : contribution.donor
      ? personLabel(contribution.donor)
      : t('contributions.form.noDonor')

  return (
    <section>
      <Link to="/contributions" className={ui.backLink}>
        <Icon name="arrowLeft" size={15} />
        {t('contributions.detail.back')}
      </Link>

      <header className={ui.pageHead}>
        <div>
          <h1>{t('contributions.detail.title', { id: contribution.id })}</h1>
          <p>{t('contributions.detail.createdAt', { date: formatDateTime(contribution.createdAt) })}</p>
        </div>
        {canAct && !isEditing && (
          <div className={ui.actions}>
            <button
              type="button"
              className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
              onClick={startEditing}
            >
              <Icon name="edit" />
              {t('contributions.detail.edit')}
            </button>
            <button
              type="button"
              className={`${ui.btn} ${ui.btnDanger} ${ui.btnSm}`}
              onClick={() => setIsCancelling(true)}
            >
              <Icon name="x" strokeWidth={2.4} />
              {t('contributions.detail.cancelContribution')}
            </button>
          </div>
        )}
      </header>

      {isAlreadyCancelled && (
        <div className={`${ui.banner} ${ui.bannerGreen} ${styles.spaced}`} role="status">
          <Icon name="check" size={17} strokeWidth={2.4} />
          <div>{t('contributions.detail.cancelSuccess')}</div>
        </div>
      )}

      {isAdjustment && contribution.adjustsContributionId && (
        <div className={`${ui.banner} ${ui.bannerAmber} ${styles.spaced}`}>
          <Icon name="repeat" size={17} />
          <div>
            {t('contributions.detail.adjustmentOf')}{' '}
            <Link to={`/contributions/${contribution.adjustsContributionId}`}>
              #{contribution.adjustsContributionId}
            </Link>
          </div>
        </div>
      )}

      <div className={styles.grid}>
        <div className={ui.card}>
          <div className={ui.cardHead}>
            <h3>{t('contributions.detail.general')}</h3>
            <MovementPill type={contribution.movementType} />
          </div>
          <table className={styles.infoTable}>
            <tbody>
              <tr>
                <th scope="row">{t('contributions.detail.donor')}</th>
                <td className={ui.cellMain}>{donorName}</td>
              </tr>
              <tr>
                <th scope="row">{t('contributions.detail.amount')}</th>
                <td className={`${ui.cellMain} ${styles.amount}`}>{formatFcfa(contribution.amount)}</td>
              </tr>
              <tr>
                <th scope="row">{t('contributions.detail.channel')}</th>
                <td>{contribution.paymentChannel.label}</td>
              </tr>
              <tr>
                <th scope="row">{t('contributions.detail.envelope')}</th>
                <td>
                  {t(
                    contribution.project
                      ? 'contributions.envelopeType.project'
                      : 'contributions.envelopeType.category',
                  )}{' '}
                  — {envelopeName}
                </td>
              </tr>
              <tr>
                <th scope="row">{t('contributions.detail.periodMonth')}</th>
                <td>{formatMonth(contribution.periodMonth)}</td>
              </tr>
              <tr>
                <th scope="row">{t('contributions.detail.transactionDate')}</th>
                <td>{formatDate(contribution.transactionDate)}</td>
              </tr>
              {contribution.reference && (
                <tr>
                  <th scope="row">{t('contributions.detail.reference')}</th>
                  <td>{contribution.reference}</td>
                </tr>
              )}
              <tr>
                <th scope="row">{t('ledger.nature')}</th>
                <td>
                  {t(`nature.${contribution.nature}`)}
                  {contribution.transferGroup && (
                    <span className={ui.cellSub}> · {contribution.transferGroup}</span>
                  )}
                </td>
              </tr>
              {contribution.activity && (
                <tr>
                  <th scope="row">{t('ledger.activity')}</th>
                  <td>{contribution.activity.name}</td>
                </tr>
              )}
              <tr>
                <th scope="row">{t('contributions.detail.notes')}</th>
                <td>{contribution.notes || '—'}</td>
              </tr>
              {contribution.remark && (
                <tr>
                  <th scope="row">{t('ledger.remark')}</th>
                  <td>{contribution.remark}</td>
                </tr>
              )}
            </tbody>
          </table>

          {isEditing && (
            <div className={styles.editBlock}>
              <div className={`${ui.banner} ${ui.bannerAmber}`}>
                <Icon name="alert" size={17} />
                <div>{t('contributions.detail.editNotice')}</div>
              </div>
              <label className={ui.checkRow}>
                <input type="checkbox" checked={anonymous} onChange={(e) => setAnonymous(e.target.checked)} />
                {t('contributions.form.anonymous')}
              </label>
              <label className={ui.field}>
                {t('contributions.form.notes')}
                <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
              </label>
              <label className={ui.field}>
                {t('ledger.remark')}
                <input value={remark} onChange={(e) => setRemark(e.target.value)} />
              </label>
              {actionError && <span className={ui.error}>{actionError}</span>}
              <div className={`${ui.actions} ${styles.right}`}>
                <button
                  type="button"
                  className={`${ui.btn} ${ui.btnSecondary}`}
                  onClick={() => setIsEditing(false)}
                >
                  {t('contributions.detail.cancelEdit')}
                </button>
                <button
                  type="button"
                  className={`${ui.btn} ${ui.btnPrimary}`}
                  disabled={updateMutation.isPending}
                  onClick={() => void saveEdit()}
                >
                  {t('contributions.detail.saveEdit')}
                </button>
              </div>
            </div>
          )}
        </div>

        <div className={styles.side}>
          {balance && (
            <div className={ui.card}>
              <div className={ui.cardHead}>
                <h3>{t('contributions.detail.balanceTitle')}</h3>
                <span className={ui.sub}>{envelopeName}</span>
              </div>
              <div className={styles.balanceTile}>
                <div className={styles.balanceLabel}>{t('contributions.detail.currentBalance')}</div>
                <div className={styles.balanceValue}>{formatFcfa(balance.currentBalance)}</div>
                <div className={ui.sub}>
                  {t('contributions.detail.balanceCount', { count: balance.contributionCount })}
                </div>
              </div>
            </div>
          )}

          <div className={ui.card}>
            <div className={ui.cardHead}>
              <h3>{t('contributions.detail.history')}</h3>
            </div>
            <ol className={styles.timeline}>
              <li>
                <span className={`${styles.dot} ${styles.dotNavy}`}>
                  <Icon name="plus" size={14} strokeWidth={2.4} />
                </span>
                <div>
                  <b>
                    {isAdjustment
                      ? t('contributions.detail.adjustmentCreated')
                      : t('contributions.detail.created')}
                  </b>
                  <div className={ui.cellSub}>{formatDateTime(contribution.createdAt)}</div>
                </div>
              </li>
            </ol>
          </div>
        </div>
      </div>

      {isCancelling && (
        <div className={styles.overlay} onClick={closeCancelDialog}>
          <div
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="cancel-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className={styles.modalHead}>
              <span className={styles.modalIcon}>
                <Icon name="alert" size={18} />
              </span>
              <h3 id="cancel-title">{t('contributions.detail.cancelTitle')}</h3>
            </div>
            <div className={styles.modalBody}>
              <p>
                {t('contributions.detail.cancelBody', {
                  id: contribution.id,
                  amount: formatFcfa(contribution.amount),
                  donor: donorName,
                  envelope: envelopeName,
                })}
              </p>
              <label className={ui.field}>
                {t('contributions.detail.cancelReasonLabel')}
                <textarea
                  rows={2}
                  autoFocus
                  value={cancelReason}
                  placeholder={t('contributions.detail.cancelReasonPlaceholder')}
                  onChange={(e) => setCancelReason(e.target.value)}
                />
              </label>
              {actionError && <span className={ui.error}>{actionError}</span>}
            </div>
            <div className={styles.modalFoot}>
              <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={closeCancelDialog}>
                {t('contributions.detail.back')}
              </button>
              <button
                type="button"
                className={`${ui.btn} ${ui.btnDanger}`}
                disabled={!cancelReason.trim() || cancelMutation.isPending}
                onClick={() => void confirmCancel()}
              >
                {t('contributions.detail.confirmCancel')}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
