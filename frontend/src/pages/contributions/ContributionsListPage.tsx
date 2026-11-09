import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { contributionsApi } from '../../features/contributions/api'
import { useContributionsQuery } from '../../features/contributions/hooks'
import type { ContributionFilters, MovementType } from '../../features/contributions/types'
import {
  useCategoriesQuery,
  usePaymentChannelsQuery,
  useProjectsQuery,
} from '../../features/reference-data/hooks'
import { downloadTextFile } from '../../lib/download'
import { INFLOW_NATURES, type MovementNature } from '../../features/ledger/types'
import { formatDate, formatMonth, formatSignedAmount, personLabel } from '../../lib/format'
import { MovementPill } from './MovementPill'
import styles from './ContributionsListPage.module.scss'

const PAGE_SIZE = 20
const MOVEMENT_TYPES: MovementType[] = ['STANDARD', 'ADJUSTMENT', 'TRANSFER']

// Le sélecteur « Enveloppe » fusionne projets et catégories : la valeur encode le type ("p:12" / "c:3").
function parseEnvelope(value: string): Pick<ContributionFilters, 'projectId' | 'categoryId'> {
  if (value.startsWith('p:')) return { projectId: Number(value.slice(2)) }
  if (value.startsWith('c:')) return { categoryId: Number(value.slice(2)) }
  return {}
}

export function ContributionsListPage() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [periodMonth, setPeriodMonth] = useState('')
  const [envelope, setEnvelope] = useState('')
  const [channelId, setChannelId] = useState('')
  const [movementType, setMovementType] = useState('')
  const [nature, setNature] = useState('')

  const activeFilters: ContributionFilters = {
    periodMonth: periodMonth || undefined,
    ...parseEnvelope(envelope),
    paymentChannelId: channelId ? Number(channelId) : undefined,
    movementType: (movementType || undefined) as MovementType | undefined,
    nature: (nature || undefined) as MovementNature | undefined,
  }
  const { data, isLoading, isError, isFetching } = useContributionsQuery({
    ...activeFilters,
    page,
    size: PAGE_SIZE,
    sort: 'transactionDate,desc',
  })
  const { data: projects } = useProjectsQuery()
  const { data: categories } = useCategoriesQuery()
  const { data: channels } = usePaymentChannelsQuery()

  const hasFilters = !!(periodMonth || envelope || channelId || movementType || nature)

  const setFilter = (setter: (value: string) => void) => (value: string) => {
    setPage(0)
    setter(value)
  }

  const resetFilters = () => {
    setPage(0)
    setPeriodMonth('')
    setEnvelope('')
    setChannelId('')
    setMovementType('')
    setNature('')
  }

  const handleExport = async () => {
    const csv = await contributionsApi.exportCsv(activeFilters)
    downloadTextFile('contributions.csv', csv)
  }

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('contributions.title')}</h1>
          {data && <p>{t('contributions.count', { count: data.totalElements })}</p>}
        </div>
        <div className={ui.actions}>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
            onClick={() => void handleExport()}
          >
            <Icon name="download" />
            {t('contributions.export')}
          </button>
          <Link to="/contributions/new" className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}>
            <Icon name="plus" strokeWidth={2.4} />
            {t('contributions.new')}
          </Link>
        </div>
      </header>

      <div className={styles.toolbar}>
        <label className={styles.filter}>
          <Icon name="filter" size={12} />
          <span>{t('contributions.filters.period')}</span>
          <input
            type="month"
            value={periodMonth}
            onChange={(e) => setFilter(setPeriodMonth)(e.target.value)}
          />
        </label>
        <label className={styles.filter}>
          <span>{t('contributions.filters.envelope')}</span>
          <select value={envelope} onChange={(e) => setFilter(setEnvelope)(e.target.value)}>
            <option value="">{t('contributions.filters.all')}</option>
            {!!categories?.length && (
              <optgroup label={t('contributions.filters.categories')}>
                {categories.map((category) => (
                  <option key={category.id} value={`c:${category.id}`}>
                    {category.name}
                  </option>
                ))}
              </optgroup>
            )}
            {!!projects?.length && (
              <optgroup label={t('contributions.filters.projects')}>
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
          <span>{t('contributions.filters.channel')}</span>
          <select value={channelId} onChange={(e) => setFilter(setChannelId)(e.target.value)}>
            <option value="">{t('contributions.filters.allMasculine')}</option>
            {channels?.map((channel) => (
              <option key={channel.id} value={channel.id}>
                {channel.label}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.filter}>
          <span>{t('ledger.nature')}</span>
          <select value={nature} onChange={(e) => setFilter(setNature)(e.target.value)}>
            <option value="">{t('contributions.filters.all')}</option>
            {[...INFLOW_NATURES, 'TRANSFER' as const].map((n) => (
              <option key={n} value={n}>
                {t(`nature.${n}`)}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.filter}>
          <span>{t('contributions.filters.movement')}</span>
          <select value={movementType} onChange={(e) => setFilter(setMovementType)(e.target.value)}>
            <option value="">{t('contributions.filters.allMasculine')}</option>
            {MOVEMENT_TYPES.map((type) => (
              <option key={type} value={type}>
                {t(`contributions.movement.${type}`)}
              </option>
            ))}
          </select>
        </label>
        {hasFilters && (
          <button type="button" className={styles.resetLink} onClick={resetFilters}>
            {t('contributions.filters.reset')}
          </button>
        )}
      </div>

      <div className={`${ui.card} ${ui.cardFlush}`}>
        {isLoading && (
          <div className={styles.skeletons} role="status" aria-label={t('contributions.loading')}>
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
            <h4>{t('contributions.error')}</h4>
          </div>
        )}

        {!isLoading && !isError && data?.content.length === 0 && (
          <div className={ui.emptyState}>
            <div className={ui.emptyIcon}>
              <Icon name="coins" size={24} />
            </div>
            <h4>{t('contributions.empty')}</h4>
            <p>{t('contributions.emptyHint')}</p>
            {hasFilters && (
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                onClick={resetFilters}
              >
                {t('contributions.filters.reset')}
              </button>
            )}
          </div>
        )}

        {!isLoading && !isError && data && data.content.length > 0 && (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <thead>
                <tr>
                  <th />
                  <th>{t('contributions.columns.donor')}</th>
                  <th>{t('contributions.columns.envelope')}</th>
                  <th>{t('contributions.columns.channel')}</th>
                  <th>{t('contributions.columns.period')}</th>
                  <th>{t('contributions.columns.status')}</th>
                  <th className={ui.num}>{t('contributions.columns.amount')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((contribution) => {
                  const isAdjustment = contribution.movementType === 'ADJUSTMENT'
                  return (
                    <tr key={contribution.id}>
                      <td className={isAdjustment ? styles.iconAmber : styles.iconGreen}>
                        <Icon name={isAdjustment ? 'repeat' : 'arrowUp'} size={18} />
                      </td>
                      <td>
                        <div className={ui.cellMain}>
                          {contribution.anonymous
                            ? t('contributions.anonymousLabel')
                            : personLabel(contribution.donor)}
                        </div>
                        <div className={ui.cellSub}>{formatDate(contribution.transactionDate)}</div>
                      </td>
                      <td>
                        {contribution.project?.name ?? contribution.category?.name ?? '—'}
                        <div className={ui.cellSub}>
                          {contribution.project
                            ? t('contributions.envelopeType.project')
                            : t('contributions.envelopeType.category')}
                        </div>
                      </td>
                      <td>{contribution.paymentChannel.label}</td>
                      <td>
                        {formatMonth(contribution.periodMonth)}
                        <div className={ui.cellSub}>{t(`nature.${contribution.nature}`)}</div>
                      </td>
                      <td>
                        <MovementPill type={contribution.movementType} />
                      </td>
                      <td className={`${ui.num} ${ui.cellMain}`}>
                        {formatSignedAmount(contribution.amount)}
                      </td>
                      <td>
                        <Link
                          to={`/contributions/${contribution.id}`}
                          className={ui.iconBtn}
                          aria-label={t('contributions.viewAction')}
                          title={t('contributions.viewAction')}
                        >
                          <Icon name="eye" size={14} />
                        </Link>
                      </td>
                    </tr>
                  )
                })}
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
            {t('contributions.pagination.previous')}
          </button>
          <span>{t('contributions.pagination.pageInfo', { page: page + 1, total: data.totalPages })}</span>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
            disabled={page + 1 >= data.totalPages || isFetching}
            onClick={() => setPage((p) => p + 1)}
          >
            {t('contributions.pagination.next')}
          </button>
        </div>
      )}
    </section>
  )
}
