import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import {
  useCancelPaymentTransactionMutation,
  useConfirmPaymentTransactionMutation,
  usePaymentTransactionsQuery,
} from '../../features/payment-transactions/hooks'
import type {
  PaymentTransactionResponse,
  PaymentTransactionStatus,
} from '../../features/payment-transactions/types'
import { ApiError } from '../../lib/apiClient'
import { formatDateTime, formatFcfa } from '../../lib/format'
import styles from './PaymentTransactionsPage.module.scss'

const PAGE_SIZE = 20
const STATUSES: PaymentTransactionStatus[] = ['PENDING', 'CONFIRMED', 'CANCELLED']

const PILL_VARIANT: Record<PaymentTransactionStatus, string | undefined> = {
  PENDING: ui.pillAmber,
  CONFIRMED: ui.pillGreen,
  CANCELLED: ui.pillGrey,
}

type PendingConfirm = { transaction: PaymentTransactionResponse }
type PendingCancel = { transaction: PaymentTransactionResponse }

// Vue back-office des dons publics (PaymentTransaction) : confirmer un don crée une Contribution
// réelle (voir PaymentTransactionService.confirm côté backend) ; annuler ne touche jamais le solde
// puisqu'aucune Contribution n'a encore été créée tant que le don est PENDING.
export function PaymentTransactionsPage() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<PaymentTransactionStatus | ''>('')

  const { data, isLoading, isError, isFetching } = usePaymentTransactionsQuery({
    status: status || undefined,
    page,
    size: PAGE_SIZE,
  })

  const confirmMutation = useConfirmPaymentTransactionMutation()
  const cancelMutation = useCancelPaymentTransactionMutation()

  const [pendingConfirm, setPendingConfirm] = useState<PendingConfirm | null>(null)
  const [pendingCancel, setPendingCancel] = useState<PendingCancel | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const hasFilters = !!status

  const setFilter = (value: PaymentTransactionStatus | '') => {
    setPage(0)
    setStatus(value)
  }

  const resetFilters = () => {
    setPage(0)
    setStatus('')
  }

  const closeConfirmDialog = () => {
    setPendingConfirm(null)
    setActionError(null)
  }

  const closeCancelDialog = () => {
    setPendingCancel(null)
    setCancelReason('')
    setActionError(null)
  }

  const runConfirm = async () => {
    if (!pendingConfirm) return
    setActionError(null)
    try {
      await confirmMutation.mutateAsync(pendingConfirm.transaction.id)
      closeConfirmDialog()
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : t('paymentTransactions.actionError'))
    }
  }

  const runCancel = async () => {
    if (!pendingCancel) return
    setActionError(null)
    try {
      await cancelMutation.mutateAsync({ id: pendingCancel.transaction.id, input: { reason: cancelReason } })
      closeCancelDialog()
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : t('paymentTransactions.actionError'))
    }
  }

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('paymentTransactions.title')}</h1>
          {data && <p>{t('paymentTransactions.count', { count: data.totalElements })}</p>}
        </div>
      </header>

      <div className={styles.toolbar}>
        <label className={styles.filter}>
          <Icon name="filter" size={12} />
          <span>{t('paymentTransactions.columns.status')}</span>
          <select value={status} onChange={(e) => setFilter(e.target.value as PaymentTransactionStatus | '')}>
            <option value="">{t('paymentTransactions.filters.all')}</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`paymentTransactions.status.${s}`)}
              </option>
            ))}
          </select>
        </label>
        {hasFilters && (
          <button type="button" className={styles.resetLink} onClick={resetFilters}>
            {t('paymentTransactions.filters.reset')}
          </button>
        )}
      </div>

      <div className={`${ui.card} ${ui.cardFlush}`}>
        {isLoading && (
          <div className={styles.skeletons} role="status" aria-label={t('paymentTransactions.loading')}>
            {[100, 100, 92, 96, 88].map((width, index) => (
              <div key={index} className={ui.skel} style={{ width: `${width}%` }} />
            ))}
          </div>
        )}

        {isError && (
          <div className={ui.emptyState}>
            <div className={ui.emptyIcon}>
              <Icon name="alert" size={24} />
            </div>
            <h4>{t('paymentTransactions.error')}</h4>
          </div>
        )}

        {!isLoading && !isError && data?.content.length === 0 && (
          <div className={ui.emptyState}>
            <div className={ui.emptyIcon}>
              <Icon name="heart" size={24} />
            </div>
            <h4>{t('paymentTransactions.empty')}</h4>
            <p>{t('paymentTransactions.emptyHint')}</p>
            {hasFilters && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                onClick={resetFilters}
              >
                {t('paymentTransactions.filters.reset')}
              </button>
            )}
          </div>
        )}

        {!isLoading && !isError && data && data.content.length > 0 && (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <thead>
                <tr>
                  <th>{t('paymentTransactions.columns.reference')}</th>
                  <th>{t('paymentTransactions.columns.donor')}</th>
                  <th>{t('paymentTransactions.columns.envelope')}</th>
                  <th>{t('paymentTransactions.columns.channel')}</th>
                  <th>{t('paymentTransactions.columns.date')}</th>
                  <th>{t('paymentTransactions.columns.status')}</th>
                  <th className={ui.num}>{t('paymentTransactions.columns.amount')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((transaction) => (
                  <tr key={transaction.id}>
                    <td className={ui.cellSub}>{transaction.reference}</td>
                    <td>
                      <div className={ui.cellMain}>
                        {transaction.anonymous
                          ? t('paymentTransactions.anonymousLabel')
                          : (transaction.donorName ?? '—')}
                      </div>
                      {transaction.donorContact && !transaction.anonymous && (
                        <div className={ui.cellSub}>{transaction.donorContact}</div>
                      )}
                    </td>
                    <td>{transaction.project?.name ?? transaction.category?.name ?? '—'}</td>
                    <td>{transaction.paymentChannel.label}</td>
                    <td className={ui.cellSub}>{formatDateTime(transaction.createdAt)}</td>
                    <td>
                      <span className={`${ui.pill} ${PILL_VARIANT[transaction.status]}`}>
                        {t(`paymentTransactions.status.${transaction.status}`)}
                      </span>
                    </td>
                    <td className={`${ui.num} ${ui.cellMain}`}>{formatFcfa(transaction.amount)}</td>
                    <td>
                      {transaction.status === 'PENDING' && (
                        <div className={styles.rowActions}>
                          <button
                            type="button"
                            className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                            onClick={() => setPendingCancel({ transaction })}
                          >
                            <Icon name="x" strokeWidth={2.4} />
                            {t('paymentTransactions.cancel')}
                          </button>
                          <button
                            type="button"
                            className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
                            onClick={() => setPendingConfirm({ transaction })}
                          >
                            <Icon name="check" strokeWidth={2.4} />
                            {t('paymentTransactions.confirm')}
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className={styles.pagination}>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
            disabled={page === 0 || isFetching}
            onClick={() => setPage((p) => p - 1)}
          >
            {t('paymentTransactions.pagination.previous')}
          </button>
          <span>
            {t('paymentTransactions.pagination.pageInfo', { page: page + 1, total: data.totalPages })}
          </span>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
            disabled={page + 1 >= data.totalPages || isFetching}
            onClick={() => setPage((p) => p + 1)}
          >
            {t('paymentTransactions.pagination.next')}
          </button>
        </div>
      )}

      {pendingConfirm && (
        <div className={styles.overlay} onClick={closeConfirmDialog}>
          <div
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="confirm-donation-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className={styles.modalHead}>
              <span className={`${styles.modalIcon} ${styles.iconNavy}`}>
                <Icon name="check" size={18} />
              </span>
              <h3 id="confirm-donation-title">{t('paymentTransactions.confirm')}</h3>
            </div>
            <div className={styles.modalBody}>
              <p>{t('paymentTransactions.confirmHint')}</p>
              <p className={ui.cellMain}>
                {formatFcfa(pendingConfirm.transaction.amount)} — {pendingConfirm.transaction.reference}
              </p>
              {actionError && <span className={ui.error}>{actionError}</span>}
            </div>
            <div className={styles.modalFoot}>
              <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={closeConfirmDialog}>
                {t('paymentTransactions.back')}
              </button>
              <button
                type="button"
                className={`${ui.btn} ${ui.btnPrimary}`}
                disabled={confirmMutation.isPending}
                onClick={() => void runConfirm()}
              >
                {t('paymentTransactions.confirm')}
              </button>
            </div>
          </div>
        </div>
      )}

      {pendingCancel && (
        <div className={styles.overlay} onClick={closeCancelDialog}>
          <div
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="cancel-donation-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className={styles.modalHead}>
              <span className={`${styles.modalIcon} ${styles.iconRed}`}>
                <Icon name="alert" size={18} />
              </span>
              <h3 id="cancel-donation-title">{t('paymentTransactions.cancelTitle')}</h3>
            </div>
            <div className={styles.modalBody}>
              <label className={ui.field}>
                {t('paymentTransactions.cancelReasonLabel')}
                <textarea
                  rows={2}
                  autoFocus
                  value={cancelReason}
                  placeholder={t('paymentTransactions.cancelReasonPlaceholder')}
                  onChange={(e) => setCancelReason(e.target.value)}
                />
              </label>
              {actionError && <span className={ui.error}>{actionError}</span>}
            </div>
            <div className={styles.modalFoot}>
              <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={closeCancelDialog}>
                {t('paymentTransactions.back')}
              </button>
              <button
                type="button"
                className={`${ui.btn} ${ui.btnDanger}`}
                disabled={!cancelReason.trim() || cancelMutation.isPending}
                onClick={() => void runCancel()}
              >
                {t('paymentTransactions.confirmCancel')}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
