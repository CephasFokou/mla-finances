import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import {
  useApproveWithdrawalMutation,
  useCancelWithdrawalMutation,
  usePayoutOptionsQuery,
  usePayoutWithdrawalMutation,
  useProcessWithdrawalMutation,
  useRecheckPayoutMutation,
  useRejectWithdrawalMutation,
  useWithdrawalCategoryBalanceQuery,
  useWithdrawalProjectBalanceQuery,
  useWithdrawalQuery,
} from '../../features/withdrawals/hooks'
import { withdrawalsApi } from '../../features/withdrawals/api'
import { ApiError } from '../../lib/apiClient'
import { downloadBlob } from '../../lib/download'
import { formatDate, formatDateTime, formatFcfa, personLabel } from '../../lib/format'
import { useAuthStore, useHasPermission } from '../../store/authStore'
import { PayoutDialog, WithdrawalPayoutCard } from './WithdrawalPayout'
import { SelfApprovedPill, WithdrawalStatusPill } from './WithdrawalStatusPill'
import styles from './WithdrawalDetailPage.module.scss'

type SimpleAction = 'approve' | 'process' | 'cancel'

export function WithdrawalDetailPage() {
  const { t } = useTranslation()
  const params = useParams<{ id: string }>()
  const id = Number(params.id)
  const { data: withdrawal, isLoading, isError } = useWithdrawalQuery(id)

  const approveMutation = useApproveWithdrawalMutation(id)
  const rejectMutation = useRejectWithdrawalMutation(id)
  const cancelMutation = useCancelWithdrawalMutation(id)
  const processMutation = useProcessWithdrawalMutation(id)
  const payoutMutation = usePayoutWithdrawalMutation(id)
  const recheckMutation = useRecheckPayoutMutation(id)
  const { data: payoutOptions } = usePayoutOptionsQuery()
  const canManage = useHasPermission('MANAGE_WITHDRAWALS')
  const canValidate = useHasPermission('VALIDATE_WITHDRAWALS')
  const currentUser = useAuthStore((state) => state.user)
  const projectBalance = useWithdrawalProjectBalanceQuery(withdrawal?.project?.id)
  const categoryBalance = useWithdrawalCategoryBalanceQuery(withdrawal?.category?.id)
  const balance = withdrawal?.project ? projectBalance.data : categoryBalance.data

  const [pendingAction, setPendingAction] = useState<SimpleAction | null>(null)
  const [isRejecting, setIsRejecting] = useState(false)
  const [rejectReason, setRejectReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)
  const [isPayingOut, setIsPayingOut] = useState(false)
  const [isDownloading, setIsDownloading] = useState(false)

  if (isLoading) {
    return (
      <div className={styles.skeletons} role="status" aria-label={t('withdrawals.loading')}>
        <div className={ui.skel} />
        <div className={ui.skel} />
        <div className={ui.skel} />
      </div>
    )
  }
  if (isError || !withdrawal) {
    return (
      <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
        <Icon name="alert" size={17} />
        <div>{t('withdrawals.error')}</div>
      </div>
    )
  }

  const envelopeName = withdrawal.project?.name ?? withdrawal.category?.name ?? '—'

  const closeRejectDialog = () => {
    setIsRejecting(false)
    setRejectReason('')
    setActionError(null)
  }

  const confirmReject = async () => {
    setActionError(null)
    try {
      await rejectMutation.mutateAsync({ reason: rejectReason })
      closeRejectDialog()
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : t('withdrawals.detail.actionError'))
    }
  }

  const runPendingAction = async () => {
    setActionError(null)
    try {
      if (pendingAction === 'approve') await approveMutation.mutateAsync()
      if (pendingAction === 'process') await processMutation.mutateAsync()
      if (pendingAction === 'cancel') await cancelMutation.mutateAsync()
      setPendingAction(null)
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : t('withdrawals.detail.actionError'))
    }
  }

  const confirmPayout = async (recipientKey: string) => {
    setActionError(null)
    try {
      await payoutMutation.mutateAsync(recipientKey)
      setIsPayingOut(false)
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : t('withdrawals.detail.actionError'))
    }
  }

  const recheckPayout = async () => {
    setActionError(null)
    try {
      await recheckMutation.mutateAsync()
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : t('withdrawals.detail.actionError'))
    }
  }

  const downloadVoucher = async () => {
    setActionError(null)
    setIsDownloading(true)
    try {
      const { blob, filename } = await withdrawalsApi.voucher(withdrawal.id)
      downloadBlob(filename ?? `bon-decaissement-${withdrawal.reference ?? `RET-${withdrawal.id}`}.pdf`, blob)
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : t('withdrawals.detail.voucherError'))
    } finally {
      setIsDownloading(false)
    }
  }

  // Séparation des rôles (vérifiée aussi côté serveur) : on ne valide ni ne rejette un retrait qu'on
  // a saisi, sauf super administrateur.
  const isOwnWithdrawal = currentUser != null && withdrawal.requestedById === currentUser.id
  const isSuperAdmin = currentUser?.roles.includes('SUPER_ADMIN') ?? false
  const ownDecisionBlocked = isOwnWithdrawal && !isSuperAdmin

  // Payout en cours (PROCESSING / PENDING_APPROVAL) : l'argent peut partir à tout moment, donc
  // ni annulation ni décaissement manuel (le backend le refuse aussi).
  const payoutActive =
    withdrawal.payoutStatus === 'PROCESSING' || withdrawal.payoutStatus === 'PENDING_APPROVAL'
  const canDecide = withdrawal.status === 'PENDING' && canValidate && !ownDecisionBlocked
  const canProcess = withdrawal.status === 'APPROVED' && !payoutActive
  const canCancel = (withdrawal.status === 'PENDING' || withdrawal.status === 'APPROVED') && !payoutActive
  const canPayout = canProcess && canManage && !!payoutOptions?.enabled

  return (
    <section>
      <Link to="/withdrawals" className={ui.backLink}>
        <Icon name="arrowLeft" size={15} />
        {t('withdrawals.detail.back')}
      </Link>

      <header className={ui.pageHead}>
        <div>
          <h1>{t('withdrawals.detail.title', { id: withdrawal.id })}</h1>
          <p>{t('withdrawals.detail.requestedAt', { date: formatDateTime(withdrawal.requestedAt) })}</p>
        </div>
        {!isRejecting && (
          <div className={ui.actions}>
            <WithdrawalStatusPill status={withdrawal.status} />
            {withdrawal.selfApproved && <SelfApprovedPill />}
            {withdrawal.status === 'PROCESSED' && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                disabled={isDownloading}
                onClick={() => void downloadVoucher()}
              >
                <Icon name="download" size={14} />
                {t('withdrawals.detail.voucher')}
              </button>
            )}
            {canDecide && (
              <>
                <button
                  type="button"
                  className={`${ui.btn} ${ui.btnDanger} ${ui.btnSm}`}
                  onClick={() => setIsRejecting(true)}
                >
                  <Icon name="x" strokeWidth={2.4} />
                  {t('withdrawals.detail.reject')}
                </button>
                <button
                  type="button"
                  className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
                  onClick={() => setPendingAction('approve')}
                >
                  <Icon name="check" strokeWidth={2.4} />
                  {t('withdrawals.detail.approve')}
                </button>
              </>
            )}
            {canPayout && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
                onClick={() => {
                  setActionError(null)
                  setIsPayingOut(true)
                }}
              >
                <Icon name="out" size={14} />
                {t(
                  withdrawal.payoutStatus === 'FAILED'
                    ? 'withdrawals.payout.retry'
                    : 'withdrawals.payout.action',
                )}
              </button>
            )}
            {canProcess && (
              <button
                type="button"
                className={`${ui.btn} ${canPayout ? ui.btnSecondary : ui.btnPrimary} ${ui.btnSm}`}
                onClick={() => setPendingAction('process')}
              >
                <Icon name="check" strokeWidth={2.4} />
                {t('withdrawals.detail.process')}
              </button>
            )}
            {canCancel && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                onClick={() => setPendingAction('cancel')}
              >
                {t('withdrawals.detail.cancel')}
              </button>
            )}
          </div>
        )}
      </header>

      {withdrawal.status === 'PENDING' && canValidate && ownDecisionBlocked && (
        <div className={`${ui.banner} ${ui.bannerNavy} ${styles.spaced}`} role="status">
          <Icon name="alert" size={17} />
          <div>{t('withdrawals.detail.ownWithdrawal')}</div>
        </div>
      )}

      {payoutActive && (
        <div className={`${ui.banner} ${ui.bannerAmber} ${styles.spaced}`} role="status">
          <Icon name="clock" size={17} />
          <div>{t('withdrawals.payout.blocked')}</div>
        </div>
      )}

      <div className={styles.grid}>
        <div className={ui.card}>
          <div className={ui.cardHead}>
            <h3>{t('withdrawals.detail.general')}</h3>
          </div>
          <table className={styles.infoTable}>
            <tbody>
              <tr>
                <th scope="row">{t('withdrawals.detail.amount')}</th>
                <td className={`${ui.cellMain} ${styles.amount}`}>{formatFcfa(withdrawal.amount)}</td>
              </tr>
              <tr>
                <th scope="row">{t('withdrawals.detail.envelope')}</th>
                <td>
                  {t(
                    withdrawal.project
                      ? 'withdrawals.envelopeType.project'
                      : 'withdrawals.envelopeType.category',
                  )}{' '}
                  — {envelopeName}
                </td>
              </tr>
              <tr>
                <th scope="row">{t('withdrawals.detail.reason')}</th>
                <td>{withdrawal.reason}</td>
              </tr>
              <tr>
                <th scope="row">{t('ledger.nature')}</th>
                <td>
                  {t(`nature.${withdrawal.nature}`)}
                  {withdrawal.transferGroup && (
                    <span className={ui.cellSub}> · {withdrawal.transferGroup}</span>
                  )}
                </td>
              </tr>
              {withdrawal.person && (
                <tr>
                  <th scope="row">{t('withdrawals.form.person')}</th>
                  <td>{personLabel(withdrawal.person)}</td>
                </tr>
              )}
              {withdrawal.operationDate && (
                <tr>
                  <th scope="row">{t('withdrawals.form.operationDate')}</th>
                  <td>{formatDate(withdrawal.operationDate)}</td>
                </tr>
              )}
              {withdrawal.paymentChannel && (
                <tr>
                  <th scope="row">{t('contributions.form.channel')}</th>
                  <td>{withdrawal.paymentChannel.label}</td>
                </tr>
              )}
              {withdrawal.activity && (
                <tr>
                  <th scope="row">{t('ledger.activity')}</th>
                  <td>{withdrawal.activity.name}</td>
                </tr>
              )}
              {withdrawal.remark && (
                <tr>
                  <th scope="row">{t('ledger.remark')}</th>
                  <td>{withdrawal.remark}</td>
                </tr>
              )}
              {(withdrawal.requestedByName || withdrawal.decidedByName) && (
                <tr>
                  <th scope="row">{t('withdrawals.detail.people')}</th>
                  <td>
                    {t('withdrawals.detail.peopleValue', {
                      requested: withdrawal.requestedByName ?? '—',
                      decided: withdrawal.decidedByName ?? '—',
                    })}
                  </td>
                </tr>
              )}
              {withdrawal.decidedAt && (
                <tr>
                  <th scope="row">{t('withdrawals.detail.decidedAtLabel')}</th>
                  <td>{formatDateTime(withdrawal.decidedAt)}</td>
                </tr>
              )}
              {withdrawal.rejectionReason && (
                <tr>
                  <th scope="row">{t('withdrawals.detail.rejectionReason')}</th>
                  <td>{withdrawal.rejectionReason}</td>
                </tr>
              )}
              {withdrawal.processedAt && (
                <tr>
                  <th scope="row">{t('withdrawals.detail.processedAtLabel')}</th>
                  <td>{formatDateTime(withdrawal.processedAt)}</td>
                </tr>
              )}
              {withdrawal.reference && (
                <tr>
                  <th scope="row">{t('withdrawals.detail.reference')}</th>
                  <td>{withdrawal.reference}</td>
                </tr>
              )}
            </tbody>
          </table>
          {actionError && !pendingAction && !isRejecting && !isPayingOut && (
            <span className={ui.error}>{actionError}</span>
          )}
        </div>

        <div className={styles.side}>
          <WithdrawalPayoutCard
            withdrawal={withdrawal}
            canRecheck={canManage}
            isRechecking={recheckMutation.isPending}
            onRecheck={() => void recheckPayout()}
          />
          {balance && (
            <div className={ui.card}>
              <div className={ui.cardHead}>
                <h3>{t('withdrawals.detail.balanceTitle')}</h3>
                <span className={ui.sub}>{envelopeName}</span>
              </div>

              {balance.deficit && (
                <div className={`${ui.banner} ${ui.bannerRed} ${styles.spaced}`} role="alert">
                  <Icon name="alert" size={17} />
                  <div>{t('withdrawals.detail.deficitAlert')}</div>
                </div>
              )}

              <div
                className={`${styles.balanceTile} ${balance.deficit ? styles.balanceDeficit : styles.balanceOk}`}
              >
                <div
                  className={`${styles.balanceLabel} ${balance.deficit ? styles.labelDeficit : styles.labelOk}`}
                >
                  {t('withdrawals.detail.provisionalBalance')}
                </div>
                <div className={styles.balanceValue}>{formatFcfa(balance.provisionalBalance)}</div>
                <div className={ui.sub}>{t('withdrawals.detail.provisionalHint')}</div>
                <div className={styles.balanceSub}>
                  <span>{t('withdrawals.detail.currentBalance')}</span>
                  <span className={ui.cellMain}>{formatFcfa(balance.currentBalance)}</span>
                </div>
                <div className={styles.balanceSub}>
                  <span>{t('withdrawals.detail.engagedAmount')}</span>
                  <span className={ui.cellMain}>{formatFcfa(balance.engagedAmount)}</span>
                </div>
              </div>

              {balance.pendingWithdrawalCount > 0 && (
                <p className={ui.sub} style={{ marginTop: 10 }}>
                  {t('withdrawals.detail.pendingCount', { count: balance.pendingWithdrawalCount })}
                </p>
              )}
            </div>
          )}
        </div>
      </div>

      {pendingAction && (
        <div className={styles.overlay} onClick={() => setPendingAction(null)}>
          <div
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="withdrawal-action-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className={styles.modalHead}>
              <span className={`${styles.modalIcon} ${styles.iconNavy}`}>
                <Icon name="check" size={18} />
              </span>
              <h3 id="withdrawal-action-title">{t(`withdrawals.detail.${pendingAction}`)}</h3>
            </div>
            <div className={styles.modalBody}>
              <p>{t(`withdrawals.detail.${pendingAction}Confirm`)}</p>
              {actionError && <span className={ui.error}>{actionError}</span>}
            </div>
            <div className={styles.modalFoot}>
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary}`}
                onClick={() => setPendingAction(null)}
              >
                {t('withdrawals.form.cancel')}
              </button>
              <button
                type="button"
                className={`${ui.btn} ${ui.btnPrimary}`}
                onClick={() => void runPendingAction()}
              >
                {t(`withdrawals.detail.${pendingAction}`)}
              </button>
            </div>
          </div>
        </div>
      )}

      {isPayingOut && payoutOptions && (
        <PayoutDialog
          withdrawal={withdrawal}
          recipients={payoutOptions.recipients}
          isSending={payoutMutation.isPending}
          error={actionError}
          onCancel={() => setIsPayingOut(false)}
          onConfirm={(recipientKey) => void confirmPayout(recipientKey)}
        />
      )}

      {isRejecting && (
        <div className={styles.overlay} onClick={closeRejectDialog}>
          <div
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="reject-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className={styles.modalHead}>
              <span className={`${styles.modalIcon} ${styles.iconRed}`}>
                <Icon name="alert" size={18} />
              </span>
              <h3 id="reject-title">{t('withdrawals.detail.rejectTitle')}</h3>
            </div>
            <div className={styles.modalBody}>
              <label className={ui.field}>
                {t('withdrawals.detail.rejectReasonLabel')}
                <textarea
                  rows={2}
                  autoFocus
                  value={rejectReason}
                  placeholder={t('withdrawals.detail.rejectReasonPlaceholder')}
                  onChange={(e) => setRejectReason(e.target.value)}
                />
              </label>
              {actionError && <span className={ui.error}>{actionError}</span>}
            </div>
            <div className={styles.modalFoot}>
              <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={closeRejectDialog}>
                {t('withdrawals.detail.back')}
              </button>
              <button
                type="button"
                className={`${ui.btn} ${ui.btnDanger}`}
                disabled={!rejectReason.trim() || rejectMutation.isPending}
                onClick={() => void confirmReject()}
              >
                {t('withdrawals.detail.confirmReject')}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
