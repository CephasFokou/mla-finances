import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import {
  useBulkReminderMutation,
  useCloseReminderMutation,
  useAcknowledgeReminderMutation,
  useReminderCaseQuery,
  useReminderOverviewQuery,
  useReminderPreviewQuery,
  useReminderReasonsQuery,
  useSendReminderMutation,
} from '../../features/reminders/hooks'
import type {
  ReminderBulkResult,
  ReminderCaseStatus,
  ReminderMonthStatus,
  ReminderRow,
  ReminderSendResult,
} from '../../features/reminders/types'
import { ApiError } from '../../lib/apiClient'
import { formatDate, formatDateTime, formatFcfa } from '../../lib/format'
import styles from './RemindersPage.module.scss'

type Filter = 'all' | 'unpaid' | 'remindable' | 'reminded' | 'toRead' | 'notFound'

const MONTH_PILL: Record<ReminderMonthStatus, string | undefined> = {
  PAID: ui.pillGreen,
  UNPAID: ui.pillAmber,
  IN_PROGRESS: ui.pillNavy,
}

const CASE_PILL: Partial<Record<ReminderCaseStatus, string | undefined>> = {
  A_RELANCER: ui.pillGrey,
  EN_ATTENTE_REPONSE: ui.pillNavy,
  ACCUSE: ui.pillNavy,
  PROMIS: ui.pillNavy,
  VERIFICATION: ui.pillAmber,
  DECLARE_NON_RETROUVE: ui.pillRed,
  REGLE: ui.pillGreen,
  SANS_SUITE: ui.pillGrey,
}

/** Mois courant à Douala (« 2026-11 ») : c'est le seul mois relançable. */
function currentDoualaMonth(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Douala', year: 'numeric', month: '2-digit' })
    .format(new Date())
    .slice(0, 7)
}

// Écran « Relances » : un motif (contribution mensuelle d'un mois, ou collecte relançable — lot 2 bis), ses membres
// concernés, relance individuelle (avec aperçu exact) ou groupée. Le serveur décide seul de l'éligibilité (délais,
// maximum, plage horaire, échéance, ciblage) : un bouton grisé affiche son motif au survol.
// « month » porte le motif : « 2026-11 » pour un mois, « P12 » pour la collecte n° 12 (voir features/reminders/api).
export function RemindersPage() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  // Lien « Ouvrir les relances » de la fiche d'une collecte : /reminders?projectId=12.
  const [month, setMonth] = useState(() => {
    const projectId = Number(searchParams.get('projectId'))
    return projectId > 0 ? `P${projectId}` : currentDoualaMonth()
  })
  const reasons = useReminderReasonsQuery()
  const isProject = month.startsWith('P')
  const [filter, setFilter] = useState<Filter>('unpaid')
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [previewFor, setPreviewFor] = useState<ReminderRow | null>(null)
  const [caseFor, setCaseFor] = useState<ReminderRow | null>(null)
  const [bulkOpen, setBulkOpen] = useState(false)
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null)

  const { data, isLoading, isError } = useReminderOverviewQuery(month)

  const rows = useMemo(() => {
    const all = data?.rows ?? []
    switch (filter) {
      case 'unpaid':
        return all.filter((r) => r.monthStatus !== 'PAID')
      case 'remindable':
        return all.filter((r) => r.canRemind)
      case 'toRead':
        return all.filter((r) => r.needsAttention)
      case 'notFound':
        return all.filter((r) => r.caseStatus === 'DECLARE_NON_RETROUVE')
      case 'reminded':
        return all.filter((r) => r.reminderCount > 0)
      default:
        return all
    }
  }, [data, filter])

  const selectable = rows.filter((r) => r.canRemind)
  const selectedRows = (data?.rows ?? []).filter((r) => selected.has(r.donorId))

  const changeMotive = (value: string) => {
    setMonth(value === 'MONTHLY' ? currentDoualaMonth() : value)
    setSelected(new Set())
    setNotice(null)
  }

  const changeMonth = (value: string) => {
    setMonth(value || currentDoualaMonth())
    setSelected(new Set())
    setNotice(null)
  }

  const toggle = (donorId: number, checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (checked) next.add(donorId)
      else next.delete(donorId)
      return next
    })
  }

  const toggleAll = (checked: boolean) => {
    setSelected(checked ? new Set(selectable.map((r) => r.donorId)) : new Set())
  }

  return (
    <section>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('reminders.title')}</h1>
          {data && data.reason === 'PROJECT' && (
            <p>
              {t('reminders.subtitleProject', {
                name: data.monthLabel,
                note: data.projectNote ?? '',
                openFrom: data.openFrom ? formatDate(data.openFrom) : '—',
                due: data.dueDate ? formatDate(data.dueDate) : '—',
              })}
            </p>
          )}
          {data && data.reason !== 'PROJECT' && (
            <p>
              {t('reminders.subtitle', {
                month: data.monthLabel,
                openFrom: formatDate(data.openFrom),
                due: formatDate(data.dueDate),
              })}
            </p>
          )}
        </div>
        <div className={styles.headActions}>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnPrimary}`}
            disabled={selectedRows.length === 0}
            onClick={() => setBulkOpen(true)}
          >
            <Icon name="repeat" strokeWidth={2.4} />
            {t('reminders.bulk.button', { count: selectedRows.length })}
          </button>
        </div>
      </header>

      {data && !data.enabled && (
        <div
          className={`${ui.banner} ${ui.bannerAmber}`}
          role="status"
          style={{ marginBottom: 'var(--space-4)' }}
        >
          <Icon name="alert" size={16} />
          <div>{t('reminders.disabled')}</div>
        </div>
      )}
      {data && !data.currentMonth && (
        <div
          className={`${ui.banner} ${ui.bannerAmber}`}
          role="status"
          style={{ marginBottom: 'var(--space-4)' }}
        >
          <Icon name="clock" size={16} />
          <div>{t(data.reason === 'PROJECT' ? 'reminders.readOnlyProject' : 'reminders.readOnlyMonth')}</div>
        </div>
      )}
      {notice && (
        <div
          className={`${ui.banner} ${notice.ok ? ui.bannerGreen : ui.bannerRed}`}
          role="status"
          style={{ marginBottom: 'var(--space-4)' }}
        >
          <Icon name={notice.ok ? 'check' : 'alert'} size={16} />
          <div>{notice.text}</div>
        </div>
      )}

      {data && (
        <div className={ui.kpiRow}>
          <Kpi
            label={t('reminders.kpi.upToDate')}
            value={`${data.summary.upToDate} / ${data.summary.members}`}
          />
          <Kpi label={t('reminders.kpi.unpaid')} value={data.summary.unpaid} />
          <Kpi label={t('reminders.kpi.remindable')} value={data.summary.remindable} />
          <Kpi label={t('reminders.kpi.sent')} value={data.summary.remindersSent} />
        </div>
      )}

      <div className={styles.toolbar}>
        <label className={styles.filter}>
          <Icon name="repeat" size={12} />
          <span>{t('reminders.motive')}</span>
          <select value={isProject ? month : 'MONTHLY'} onChange={(e) => changeMotive(e.target.value)}>
            <option value="MONTHLY">{t('reminders.motiveMonthly')}</option>
            {(reasons.data ?? [])
              .filter((r) => r.reason === 'PROJECT' && r.projectId !== null)
              .map((r) => (
                <option key={r.projectId} value={`P${r.projectId}`}>
                  {r.label}
                </option>
              ))}
            {isProject && !(reasons.data ?? []).some((r) => `P${r.projectId}` === month) && data && (
              <option value={month}>{data.monthLabel}</option>
            )}
          </select>
        </label>
        {!isProject && (
          <label className={styles.filter}>
            <Icon name="clock" size={12} />
            <span>{t('reminders.month')}</span>
            <input
              type="month"
              value={month}
              max={currentDoualaMonth()}
              onChange={(e) => changeMonth(e.target.value)}
            />
          </label>
        )}
        <div className={ui.seg} role="tablist" aria-label={t('reminders.filters.label')}>
          {(['unpaid', 'remindable', 'toRead', 'notFound', 'reminded', 'all'] as Filter[]).map((f) => (
            <button
              key={f}
              type="button"
              role="tab"
              aria-selected={filter === f}
              className={filter === f ? ui.segActive : undefined}
              onClick={() => setFilter(f)}
            >
              {t(`reminders.filters.${f}`)}
            </button>
          ))}
        </div>
      </div>

      <div className={`${ui.card} ${ui.cardFlush}`}>
        {isLoading && (
          <div className={styles.skeletons} role="status" aria-label={t('reminders.loading')}>
            {[100, 96, 92, 88].map((width, index) => (
              <div key={index} className={ui.skel} style={{ width: `${width}%` }} />
            ))}
          </div>
        )}
        {isError && (
          <div className={ui.emptyState}>
            <div className={ui.emptyIcon}>
              <Icon name="alert" size={24} />
            </div>
            <h4>{t('reminders.error')}</h4>
          </div>
        )}
        {!isLoading && !isError && rows.length === 0 && (
          <div className={ui.emptyState}>
            <div className={ui.emptyIcon}>
              <Icon name="check" size={24} />
            </div>
            <h4>{t('reminders.empty')}</h4>
          </div>
        )}
        {!isLoading && !isError && rows.length > 0 && data && (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <thead>
                <tr>
                  <th className={styles.checkCell}>
                    <input
                      type="checkbox"
                      aria-label={t('reminders.selectAll')}
                      disabled={selectable.length === 0}
                      checked={selectable.length > 0 && selectable.every((r) => selected.has(r.donorId))}
                      onChange={(e) => toggleAll(e.target.checked)}
                    />
                  </th>
                  <th>{t('reminders.columns.member')}</th>
                  {!isProject && <th className={ui.num}>{t('reminders.columns.expected')}</th>}
                  <th>{t(isProject ? 'reminders.columns.project' : 'reminders.columns.month')}</th>
                  <th>{t('reminders.columns.lastReminder')}</th>
                  <th>{t('reminders.columns.case')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.donorId}>
                    <td className={styles.checkCell}>
                      <input
                        type="checkbox"
                        aria-label={t('reminders.select', { name: row.label })}
                        disabled={!row.canRemind}
                        checked={selected.has(row.donorId)}
                        onChange={(e) => toggle(row.donorId, e.target.checked)}
                      />
                    </td>
                    <td>
                      <div className={ui.cellMain}>{row.label}</div>
                      <div className={ui.cellSub}>
                        {t(`reminders.role.${row.role}`)}
                        {' · '}
                        {row.hasEmail ? t('reminders.contact.email') : t('reminders.contact.noEmail')}
                      </div>
                    </td>
                    {!isProject && (
                      <td className={`${ui.num} ${ui.cellSub}`}>{formatFcfa(row.expectedAmount ?? 0)}</td>
                    )}
                    <td>
                      <span className={`${ui.pill} ${MONTH_PILL[row.monthStatus]}`}>
                        {t(
                          `${isProject ? 'reminders.projectStatus' : 'reminders.monthStatus'}.${row.monthStatus}`,
                        )}
                      </span>
                    </td>
                    <td>
                      {row.lastReminderAt ? (
                        <>
                          <div className={ui.cellMain}>{formatDateTime(row.lastReminderAt)}</div>
                          <div className={ui.cellSub}>
                            {t('reminders.count', { count: row.reminderCount, max: data.maxReminders })}
                            {row.lastDeliveries.map((d) => (
                              <span key={d.channel}>
                                {' · '}
                                {t(`reminders.channel.${d.channel}`)} {t(`reminders.delivery.${d.status}`)}
                              </span>
                            ))}
                          </div>
                        </>
                      ) : row.lastReminderScheduledFor ? (
                        <div className={ui.cellSub}>
                          {t('reminders.scheduledFor', {
                            date: formatDateTime(row.lastReminderScheduledFor),
                          })}
                        </div>
                      ) : (
                        <span className={ui.cellSub}>—</span>
                      )}
                    </td>
                    <td>
                      {row.caseStatus ? (
                        <button type="button" className={styles.caseLink} onClick={() => setCaseFor(row)}>
                          <span className={`${ui.pill} ${CASE_PILL[row.caseStatus] ?? ui.pillGrey}`}>
                            {t(`reminders.caseStatus.${row.caseStatus}`)}
                          </span>
                        </button>
                      ) : (
                        <span className={ui.cellSub}>—</span>
                      )}
                      {row.caseStatus === 'PROMIS' && row.promisedDate && (
                        <div className={ui.cellSub}>
                          {t('reminders.promisedOn', { date: formatDate(row.promisedDate) })}
                        </div>
                      )}
                      {row.needsAttention && (
                        <div>
                          <span className={`${ui.pill} ${ui.pillAmber}`}>{t('reminders.toRead')}</span>
                        </div>
                      )}
                    </td>
                    <td>
                      <div className={styles.rowActions}>
                        <span title={row.blockedReason ?? undefined}>
                          <button
                            type="button"
                            className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
                            disabled={!row.canRemind}
                            onClick={() => setPreviewFor(row)}
                          >
                            <Icon name="repeat" strokeWidth={2.4} />
                            {t('reminders.remind')}
                          </button>
                        </span>
                        {!row.canRemind && row.blockedReason && (
                          <div className={styles.blockedReason}>{row.blockedReason}</div>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {previewFor && (
        <PreviewDialog
          row={previewFor}
          month={month}
          onClose={() => setPreviewFor(null)}
          onDone={(text, ok) => {
            setPreviewFor(null)
            setNotice({ ok, text })
          }}
        />
      )}
      {caseFor && <CaseDialog row={caseFor} month={month} onClose={() => setCaseFor(null)} />}
      {bulkOpen && (
        <BulkDialog
          rows={selectedRows}
          month={month}
          onClose={() => setBulkOpen(false)}
          onDone={() => setSelected(new Set())}
        />
      )}
    </section>
  )
}

function Kpi({ label, value }: { label: string; value: string | number }) {
  return (
    <div className={ui.card}>
      <div className={ui.kpiLabel}>
        <span>{label}</span>
      </div>
      <div className={ui.kpiValue}>{value}</div>
    </div>
  )
}

function useErrorMessage() {
  const { t } = useTranslation()
  return (error: unknown) => (error instanceof ApiError ? error.message : t('reminders.genericError'))
}

function sendSummary(
  t: (key: string, options?: Record<string, unknown>) => string,
  label: string,
  r: ReminderSendResult,
) {
  if (r.queued && r.scheduledFor)
    return t('reminders.result.queued', { name: label, date: formatDateTime(r.scheduledFor) })
  if (r.sent) return t('reminders.result.sent', { name: label })
  const error = r.deliveries.find((d) => d.error)?.error ?? ''
  return t('reminders.result.failed', { name: label, error })
}

function PreviewDialog({
  row,
  month,
  onClose,
  onDone,
}: {
  row: ReminderRow
  month: string
  onClose: () => void
  onDone: (text: string, ok: boolean) => void
}) {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const { data, isLoading, isError } = useReminderPreviewQuery(row.donorId, month)
  const sendMutation = useSendReminderMutation()
  const [error, setError] = useState<string | null>(null)

  const send = async () => {
    setError(null)
    try {
      const result = await sendMutation.mutateAsync({ donorId: row.donorId, month })
      onDone(sendSummary(t, row.label, result), result.sent || result.queued)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div
        className={`${styles.modal} ${styles.modalWide}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="reminder-preview-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.modalHead}>
          <span className={`${styles.modalIcon} ${styles.iconNavy}`}>
            <Icon name="repeat" size={18} />
          </span>
          <h3 id="reminder-preview-title">{t('reminders.preview.title', { name: row.label })}</h3>
        </div>
        <div className={styles.modalBody}>
          {isLoading && <div className={ui.skel} style={{ height: 240 }} />}
          {isError && <span className={ui.error}>{t('reminders.preview.error')}</span>}
          {data && (
            <>
              <p>
                {data.channels.length > 0
                  ? t('reminders.preview.to', {
                      recipients: data.channels
                        .map((c) => `${t(`reminders.channel.${c.channel}`)} ${c.recipient}`)
                        .join(', '),
                    })
                  : t('reminders.preview.noChannel')}
              </p>
              {data.whatsappText && (
                <>
                  <p className={ui.cellMain}>{t('reminders.preview.whatsapp')}</p>
                  <pre className={styles.whatsappPreview}>{data.whatsappText}</pre>
                </>
              )}
              <p className={ui.cellMain}>{data.subject}</p>
              <iframe
                title={t('reminders.preview.frameTitle')}
                className={styles.previewFrame}
                sandbox=""
                srcDoc={data.html}
              />
              {!data.canRemind && data.blockedReason && (
                <span className={ui.error}>{data.blockedReason}</span>
              )}
            </>
          )}
          {error && <span className={ui.error}>{error}</span>}
        </div>
        <div className={styles.modalFoot}>
          <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={onClose}>
            {t('reminders.back')}
          </button>
          <button
            type="button"
            className={`${ui.btn} ${ui.btnPrimary}`}
            disabled={!data?.canRemind || sendMutation.isPending}
            onClick={() => void send()}
          >
            {t('reminders.preview.send')}
          </button>
        </div>
      </div>
    </div>
  )
}

function BulkDialog({
  rows,
  month,
  onClose,
  onDone,
}: {
  rows: ReminderRow[]
  month: string
  onClose: () => void
  onDone: () => void
}) {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const mutation = useBulkReminderMutation()
  const [result, setResult] = useState<ReminderBulkResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const run = async () => {
    setError(null)
    try {
      setResult(await mutation.mutateAsync({ donorIds: rows.map((r) => r.donorId), month }))
      onDone()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="reminder-bulk-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.modalHead}>
          <span className={`${styles.modalIcon} ${styles.iconNavy}`}>
            <Icon name="users" size={18} />
          </span>
          <h3 id="reminder-bulk-title">{t('reminders.bulk.title')}</h3>
        </div>
        <div className={styles.modalBody}>
          {!result && (
            <>
              <p>{t('reminders.bulk.confirm', { count: rows.length })}</p>
              <p className={ui.cellSub}>{rows.map((r) => r.label).join(', ')}</p>
            </>
          )}
          {result && (
            <>
              <p className={ui.cellMain}>
                {t('reminders.bulk.report', { sent: result.sent.length, skipped: result.skipped.length })}
              </p>
              {result.sent.some((s) => s.queued) && (
                <p className={ui.cellSub}>{t('reminders.bulk.queuedHint')}</p>
              )}
              {result.skipped.length > 0 && (
                <ul className={styles.skippedList}>
                  {result.skipped.map((s) => (
                    <li key={s.donorId}>
                      <b>{s.label}</b> — {s.reason}
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
          {error && <span className={ui.error}>{error}</span>}
        </div>
        <div className={styles.modalFoot}>
          <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={onClose}>
            {result ? t('reminders.close') : t('reminders.back')}
          </button>
          {!result && (
            <button
              type="button"
              className={`${ui.btn} ${ui.btnPrimary}`}
              disabled={mutation.isPending}
              onClick={() => void run()}
            >
              {mutation.isPending
                ? t('reminders.bulk.sending')
                : t('reminders.bulk.send', { count: rows.length })}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

function CaseDialog({ row, month, onClose }: { row: ReminderRow; month: string; onClose: () => void }) {
  const { t } = useTranslation()
  const errorMessage = useErrorMessage()
  const { data, isLoading, isError } = useReminderCaseQuery(row.caseId)
  const closeMutation = useCloseReminderMutation()
  const ackMutation = useAcknowledgeReminderMutation()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const close = async () => {
    setError(null)
    try {
      await closeMutation.mutateAsync({ donorId: row.donorId, month, reason })
      setReason('')
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  const open = data && data.status !== 'REGLE' && data.status !== 'SANS_SUITE'

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div
        className={`${styles.modal} ${styles.modalWide}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="reminder-case-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.modalHead}>
          <span className={`${styles.modalIcon} ${styles.iconNavy}`}>
            <Icon name="clock" size={18} />
          </span>
          <h3 id="reminder-case-title">{t('reminders.case.title', { name: row.label })}</h3>
        </div>
        <div className={styles.modalBody}>
          {isLoading && <div className={ui.skel} style={{ height: 160 }} />}
          {isError && <span className={ui.error}>{t('reminders.case.error')}</span>}
          {data && (
            <>
              <p>
                <span className={`${ui.pill} ${CASE_PILL[data.status] ?? ui.pillGrey}`}>
                  {t(`reminders.caseStatus.${data.status}`)}
                </span>{' '}
                {data.monthLabel}
                {data.status === 'PROMIS' && data.promisedDate
                  ? ` · ${t('reminders.promisedOn', { date: formatDate(data.promisedDate) })}`
                  : ''}
              </p>
              {data.needsAttention && (
                <div className={`${ui.banner} ${ui.bannerAmber}`} role="status">
                  <Icon name="alert" size={16} />
                  <div>
                    {data.status === 'DECLARE_NON_RETROUVE'
                      ? t('reminders.case.notFoundHint')
                      : t('reminders.case.toReadHint')}
                  </div>
                </div>
              )}
              <ol className={styles.timeline}>
                {data.reminders.map((r) => (
                  <li key={r.id}>
                    <div className={ui.cellMain}>
                      {t('reminders.case.reminder', { n: r.sequenceNo })}
                      {' · '}
                      {t(`reminders.trigger.${r.trigger}`)}
                      {r.sentBy ? ` · ${r.sentBy}` : ''}
                    </div>
                    <div className={ui.cellSub}>
                      {r.sentAt
                        ? t('reminders.case.sentAt', { date: formatDateTime(r.sentAt) })
                        : r.cancelledAt
                          ? t('reminders.case.cancelledAt', { date: formatDateTime(r.cancelledAt) })
                          : r.scheduledFor
                            ? t('reminders.scheduledFor', { date: formatDateTime(r.scheduledFor) })
                            : ''}
                    </div>
                    {r.deliveries.map((d, i) => (
                      <div key={i} className={ui.cellSub}>
                        {t(`reminders.channel.${d.channel}`)} {d.recipient} —{' '}
                        {t(`reminders.delivery.${d.status}`)}
                        {d.error ? ` (${d.error})` : ''}
                      </div>
                    ))}
                  </li>
                ))}
                {data.responses.map((r, i) => (
                  <li key={`r${i}`}>
                    <div className={ui.cellMain}>
                      {t(`reminders.response.${r.type}`)}
                      {' · '}
                      {t(`reminders.channel.${r.channel}`)}
                    </div>
                    <div className={ui.cellSub}>{formatDateTime(r.receivedAt)}</div>
                    {r.text && r.channel === 'WHATSAPP' && <div className={styles.quote}>« {r.text} »</div>}
                    {r.outcome && <div className={ui.cellSub}>{r.outcome}</div>}
                  </li>
                ))}
                {data.closedAt && (
                  <li>
                    <div className={ui.cellMain}>
                      {t('reminders.case.closed', { date: formatDateTime(data.closedAt) })}
                      {data.closedBy ? ` · ${data.closedBy}` : ''}
                    </div>
                    {data.closedReason && <div className={ui.cellSub}>{data.closedReason}</div>}
                    {data.contributionId && (
                      <a className={styles.caseLink} href={`/contributions/${data.contributionId}`}>
                        {t('reminders.case.contribution', { id: data.contributionId })}
                      </a>
                    )}
                  </li>
                )}
              </ol>
              {open && (
                <label className={ui.field}>
                  {t('reminders.case.closeLabel')}
                  <textarea
                    rows={2}
                    value={reason}
                    maxLength={300}
                    placeholder={t('reminders.case.closePlaceholder')}
                    onChange={(e) => setReason(e.target.value)}
                  />
                </label>
              )}
            </>
          )}
          {error && <span className={ui.error}>{error}</span>}
        </div>
        <div className={styles.modalFoot}>
          <button type="button" className={`${ui.btn} ${ui.btnSecondary}`} onClick={onClose}>
            {t('reminders.close')}
          </button>
          {data?.needsAttention && (
            <button
              type="button"
              className={`${ui.btn} ${ui.btnPrimary}`}
              disabled={ackMutation.isPending}
              onClick={() => void ackMutation.mutateAsync(data.id).catch((e) => setError(errorMessage(e)))}
            >
              {t('reminders.case.markRead')}
            </button>
          )}
          {open && (
            <button
              type="button"
              className={`${ui.btn} ${ui.btnDanger}`}
              disabled={!reason.trim() || closeMutation.isPending}
              onClick={() => void close()}
            >
              {t('reminders.case.closeAction')}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
