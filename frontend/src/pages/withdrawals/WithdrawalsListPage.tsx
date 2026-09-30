import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useWithdrawalsQuery } from '../../features/withdrawals/hooks'
import type { WithdrawalFilters, WithdrawalStatus } from '../../features/withdrawals/types'
import { useCategoriesQuery, useProjectsQuery } from '../../features/reference-data/hooks'
import { formatDate, formatFcfa } from '../../lib/format'
import { WithdrawalStatusPill } from './WithdrawalStatusPill'
import styles from './WithdrawalsListPage.module.scss'

const PAGE_SIZE = 20
const STATUSES: WithdrawalStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'PROCESSED', 'CANCELLED']

// Le sélecteur « Enveloppe » fusionne projets et catégories : la valeur encode le type ("p:12" / "c:3").
function parseEnvelope(value: string): Pick<WithdrawalFilters, 'projectId' | 'categoryId'> {
  if (value.startsWith('p:')) return { projectId: Number(value.slice(2)) }
  if (value.startsWith('c:')) return { categoryId: Number(value.slice(2)) }
  return {}
}

export function WithdrawalsListPage() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [envelope, setEnvelope] = useState('')
  const [status, setStatus] = useState('')

  const activeFilters: WithdrawalFilters = {
    ...parseEnvelope(envelope),
    status: (status || undefined) as WithdrawalStatus | undefined,
  }
  const { data, isLoading, isError, isFetching } = useWithdrawalsQuery({
    ...activeFilters,
    page,
    size: PAGE_SIZE,
    sort: 'requestedAt,desc',
  })
  const { data: projects } = useProjectsQuery()
  const { data: categories } = useCategoriesQuery()

  const hasFilters = !!(envelope || status)

  const setFilter = (setter: (value: string) => void) => (value: string) => {
    setPage(0)
    setter(value)
  }

  const resetFilters = () => {
    setPage(0)
    setEnvelope('')
    setStatus('')
  }

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('withdrawals.title')}</h1>
          {data && <p>{t('withdrawals.count', { count: data.totalElements })}</p>}
        </div>
        <div className={ui.actions}>
          <Link to="/withdrawals/new" className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}>
            <Icon name="plus" strokeWidth={2.4} />
            {t('withdrawals.new')}
          </Link>
        </div>
      </header>

      <div className={styles.toolbar}>
        <label className={styles.filter}>
          <Icon name="filter" size={12} />
          <span>{t('withdrawals.filters.envelope')}</span>
          <select value={envelope} onChange={(e) => setFilter(setEnvelope)(e.target.value)}>
            <option value="">{t('withdrawals.filters.all')}</option>
            {!!categories?.length && (
              <optgroup label={t('withdrawals.filters.categories')}>
                {categories.map((category) => (
                  <option key={category.id} value={`c:${category.id}`}>
                    {category.name}
                  </option>
                ))}
              </optgroup>
            )}
            {!!projects?.length && (
              <optgroup label={t('withdrawals.filters.projects')}>
                {projects.map((project) => (
                  <option key={project.id} value={`p:${project.id}`}>
                    {project.name}
                  </option>
                ))}
              </optgroup>
            )}
          </select>
        </label>
        <label className={styles.filter}>
          <span>{t('withdrawals.filters.status')}</span>
          <select value={status} onChange={(e) => setFilter(setStatus)(e.target.value)}>
            <option value="">{t('withdrawals.filters.allMasculine')}</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`withdrawals.status.${s}`)}
              </option>
            ))}
          </select>
        </label>
        {hasFilters && (
          <button type="button" className={styles.resetLink} onClick={resetFilters}>
            {t('withdrawals.filters.reset')}
          </button>
        )}
      </div>

      <div className={`${ui.card} ${ui.cardFlush}`}>
        {isLoading && (
          <div className={styles.skeletons} role="status" aria-label={t('withdrawals.loading')}>
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
            <h4>{t('withdrawals.error')}</h4>
          </div>
        )}

        {!isLoading && !isError && data?.content.length === 0 && (
          <div className={ui.emptyState}>
            <div className={ui.emptyIcon}>
              <Icon name="out" size={24} />
            </div>
            <h4>{t('withdrawals.empty')}</h4>
            <p>{t('withdrawals.emptyHint')}</p>
            {hasFilters && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                onClick={resetFilters}
              >
                {t('withdrawals.filters.reset')}
              </button>
            )}
          </div>
        )}

        {!isLoading && !isError && data && data.content.length > 0 && (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <thead>
                <tr>
                  <th>{t('withdrawals.columns.envelope')}</th>
                  <th>{t('withdrawals.columns.reason')}</th>
                  <th>{t('withdrawals.columns.status')}</th>
                  <th>{t('withdrawals.columns.requestedAt')}</th>
                  <th className={ui.num}>{t('withdrawals.columns.amount')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((withdrawal) => (
                  <tr key={withdrawal.id}>
                    <td>
                      <div className={ui.cellMain}>
                        {withdrawal.project?.name ?? withdrawal.category?.name ?? '—'}
                      </div>
                      <div className={ui.cellSub}>
                        {withdrawal.project
                          ? t('withdrawals.envelopeType.project')
                          : t('withdrawals.envelopeType.category')}
                      </div>
                    </td>
                    <td className={ui.cellSub}>{withdrawal.reason}</td>
                    <td>
                      <WithdrawalStatusPill status={withdrawal.status} />
                    </td>
                    <td>{formatDate(withdrawal.requestedAt)}</td>
                    <td className={`${ui.num} ${ui.cellMain}`}>{formatFcfa(withdrawal.amount)}</td>
                    <td>
                      <Link
                        to={`/withdrawals/${withdrawal.id}`}
                        className={ui.iconBtn}
                        aria-label={t('withdrawals.viewAction')}
                        title={t('withdrawals.viewAction')}
                      >
                        <Icon name="eye" size={14} />
                      </Link>
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
            {t('withdrawals.pagination.previous')}
          </button>
          <span>{t('withdrawals.pagination.pageInfo', { page: page + 1, total: data.totalPages })}</span>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
            disabled={page + 1 >= data.totalPages || isFetching}
            onClick={() => setPage((p) => p + 1)}
          >
            {t('withdrawals.pagination.next')}
          </button>
        </div>
      )}
    </section>
  )
}
