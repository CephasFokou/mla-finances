import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { PersonPicker } from '../../components/ledger/PersonPicker'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useActivitiesQuery } from '../../features/ledger/hooks'
import { OUTFLOW_NATURES, type MovementNature } from '../../features/ledger/types'
import {
  useCategoriesQuery,
  usePaymentChannelsQuery,
  useProjectsQuery,
} from '../../features/reference-data/hooks'
import { useCreateWithdrawalMutation } from '../../features/withdrawals/hooks'
import { ApiError } from '../../lib/apiClient'
import { useHasPermission } from '../../store/authStore'
import styles from './WithdrawalFormPage.module.scss'

const today = new Date().toISOString().slice(0, 10)

const schema = z
  .object({
    mode: z.enum(['request', 'record']),
    envelopeType: z.enum(['project', 'category']),
    projectId: z.string().optional(),
    categoryId: z.string().optional(),
    nature: z.string().min(1),
    amount: z
      .string()
      .min(1)
      .refine((v) => Number(v) > 0, { message: 'Le montant doit être supérieur à 0' }),
    reason: z.string().min(1, 'Le motif est requis'),
    personId: z.string().optional(),
    paymentChannelId: z.string().optional(),
    activityId: z.string().optional(),
    operationDate: z.string().optional(),
    remark: z.string().optional(),
  })
  .refine((v) => (v.envelopeType === 'project' ? !!v.projectId : !!v.categoryId), {
    message: 'Sélectionnez une collecte ou la caisse',
    path: ['projectId'],
  })
  .refine((v) => v.mode === 'request' || (!!v.operationDate && v.operationDate <= today), {
    message: 'Indiquez la date de la sortie (pas dans le futur)',
    path: ['operationDate'],
  })

type FormValues = z.infer<typeof schema>

/**
 * Sortie d'argent sur la caisse du MLA ou une collecte. Deux modes : une demande qui suit le
 * workflow (validation puis décaissement), ou — pour qui peut à la fois demander et valider — une
 * sortie déjà effectuée, enregistrée directement comme décaissée à sa date réelle (cahier de caisse).
 */
export function WithdrawalFormPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: projects } = useProjectsQuery()
  const { data: categories } = useCategoriesQuery()
  const { data: channels } = usePaymentChannelsQuery()
  const { data: activities } = useActivitiesQuery()
  const createMutation = useCreateWithdrawalMutation()
  const canRecord = useHasPermission('MANAGE_WITHDRAWALS', 'VALIDATE_WITHDRAWALS')
  const [submitError, setSubmitError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { mode: 'request', envelopeType: 'category', nature: 'EXPENSE', operationDate: today },
  })

  const envelopeType = watch('envelopeType')
  const mode = watch('mode')
  const categoryId = watch('categoryId')

  // La caisse principale est l'enveloppe par défaut d'une dépense courante.
  const mainFund = categories?.find((c) => c.mainFund)
  useEffect(() => {
    if (mainFund && !categoryId) setValue('categoryId', String(mainFund.id))
  }, [mainFund, categoryId, setValue])
  useEffect(() => {
    if (canRecord) setValue('mode', 'record')
  }, [canRecord, setValue])

  // Une sortie ne se fait que sur une collecte en cours (une collecte clôturée est soldée).
  const activeProjects = useMemo(() => projects?.filter((p) => p.status === 'ACTIVE') ?? [], [projects])

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    try {
      const created = await createMutation.mutateAsync({
        projectId: values.envelopeType === 'project' ? Number(values.projectId) : undefined,
        categoryId: values.envelopeType === 'category' ? Number(values.categoryId) : undefined,
        amount: Number(values.amount),
        reason: values.reason,
        nature: values.nature as MovementNature,
        personId: values.personId ? Number(values.personId) : undefined,
        paymentChannelId: values.paymentChannelId ? Number(values.paymentChannelId) : undefined,
        activityId:
          values.envelopeType === 'category' && values.activityId ? Number(values.activityId) : undefined,
        operationDate: values.mode === 'record' ? values.operationDate : undefined,
        remark: values.remark || undefined,
      })
      navigate(`/withdrawals/${created.id}`)
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : t('withdrawals.form.genericError'))
    }
  })

  const segButton = (active: boolean, onClick: () => void, label: string) => (
    <button
      type="button"
      className={active ? ui.segActive : undefined}
      aria-pressed={active}
      onClick={onClick}
    >
      {label}
    </button>
  )

  return (
    <section>
      <Link to="/withdrawals" className={ui.backLink}>
        <Icon name="arrowLeft" size={15} />
        {t('withdrawals.detail.back')}
      </Link>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('withdrawals.form.title')}</h1>
          <p>{t('withdrawals.form.subtitle')}</p>
        </div>
      </header>

      <form className={`${ui.card} ${styles.form}`} onSubmit={onSubmit} noValidate>
        <div className={ui.formGrid}>
          {canRecord && (
            <div className={`${ui.field} ${ui.span2}`}>
              <span>{t('withdrawals.form.mode')}</span>
              <div className={ui.seg} role="group" aria-label={t('withdrawals.form.mode')}>
                {segButton(
                  mode === 'record',
                  () => setValue('mode', 'record'),
                  t('withdrawals.form.modeRecord'),
                )}
                {segButton(
                  mode === 'request',
                  () => setValue('mode', 'request'),
                  t('withdrawals.form.modeRequest'),
                )}
              </div>
              <span className={ui.hint}>
                {mode === 'record'
                  ? t('withdrawals.form.modeRecordHint')
                  : t('withdrawals.form.modeRequestHint')}
              </span>
            </div>
          )}

          <div className={`${ui.field} ${ui.span2}`}>
            <span>{t('withdrawals.form.envelope')}</span>
            <div className={ui.seg} role="group" aria-label={t('withdrawals.form.envelope')}>
              {segButton(
                envelopeType === 'category',
                () => setValue('envelopeType', 'category'),
                t('ledger.mainFundOption'),
              )}
              {segButton(
                envelopeType === 'project',
                () => setValue('envelopeType', 'project'),
                t('ledger.collectionOption'),
              )}
            </div>
            <span className={ui.hint}>{t('ledger.envelopeHint')}</span>
          </div>

          {envelopeType === 'project' ? (
            <label className={`${ui.field} ${ui.span2}`}>
              {t('ledger.collection')}
              <select {...register('projectId')}>
                <option value="">{t('withdrawals.form.selectPlaceholder')}</option>
                {activeProjects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.name}
                  </option>
                ))}
              </select>
              {errors.projectId && <span className={ui.error}>{errors.projectId.message}</span>}
            </label>
          ) : (
            <>
              <label className={ui.field}>
                {t('withdrawals.form.category')}
                <select {...register('categoryId')}>
                  <option value="">{t('withdrawals.form.selectPlaceholder')}</option>
                  {categories?.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className={ui.field}>
                {t('ledger.activity')}
                <select {...register('activityId')}>
                  <option value="">{t('ledger.noActivity')}</option>
                  {activities?.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                    </option>
                  ))}
                </select>
              </label>
            </>
          )}

          <label className={ui.field}>
            {t('ledger.nature')}
            <select {...register('nature')}>
              {OUTFLOW_NATURES.map((n) => (
                <option key={n} value={n}>
                  {t(`nature.${n}`)}
                </option>
              ))}
            </select>
          </label>

          <label className={ui.field}>
            {t('withdrawals.form.amount')}
            <input type="number" inputMode="decimal" min="0" step="1" {...register('amount')} />
            {errors.amount && <span className={ui.error}>{t('withdrawals.form.amountError')}</span>}
          </label>

          {mode === 'record' && (
            <label className={ui.field}>
              {t('withdrawals.form.operationDate')}
              <input type="date" max={today} {...register('operationDate')} />
              {errors.operationDate && <span className={ui.error}>{errors.operationDate.message}</span>}
            </label>
          )}

          <label className={ui.field}>
            {t('contributions.form.channel')}
            <select {...register('paymentChannelId')}>
              <option value="">{t('withdrawals.form.noChannel')}</option>
              {channels?.map((channel) => (
                <option key={channel.id} value={channel.id}>
                  {channel.label}
                </option>
              ))}
            </select>
          </label>

          <div className={`${ui.field} ${ui.span2}`}>
            <span>{t('withdrawals.form.person')}</span>
            <PersonPicker
              label={t('withdrawals.form.person')}
              registration={register('personId')}
              emptyLabel={t('withdrawals.form.noPerson')}
            />
          </div>

          <label className={`${ui.field} ${ui.span2}`}>
            {t('withdrawals.form.reason')}
            <textarea
              rows={2}
              placeholder={t('withdrawals.form.reasonPlaceholder')}
              {...register('reason')}
            />
            {errors.reason && <span className={ui.error}>{t('withdrawals.form.reasonError')}</span>}
          </label>

          <label className={`${ui.field} ${ui.span2}`}>
            {t('ledger.remark')}
            <input placeholder={t('ledger.remarkPlaceholder')} {...register('remark')} />
          </label>
        </div>

        <div className={`${ui.banner} ${ui.bannerNavy}`}>
          <Icon name="clock" size={17} />
          <div>
            {mode === 'record' ? t('withdrawals.form.recordNotice') : t('withdrawals.form.workflowNotice')}
          </div>
        </div>

        {submitError && (
          <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
            <Icon name="alert" size={17} />
            <div>{submitError}</div>
          </div>
        )}

        <div className={`${ui.actions} ${styles.formActions}`}>
          <Link to="/withdrawals" className={`${ui.btn} ${ui.btnSecondary}`}>
            {t('withdrawals.form.cancel')}
          </Link>
          <button type="submit" className={`${ui.btn} ${ui.btnPrimary}`} disabled={isSubmitting}>
            {mode === 'record' ? t('withdrawals.form.submitRecord') : t('withdrawals.form.submit')}
          </button>
        </div>
      </form>
    </section>
  )
}
