import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { PersonPicker } from '../../components/ledger/PersonPicker'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useCreateContributionMutation } from '../../features/contributions/hooks'
import { useActivitiesQuery } from '../../features/ledger/hooks'
import { INFLOW_NATURES, type MovementNature } from '../../features/ledger/types'
import {
  useCategoriesQuery,
  usePaymentChannelsQuery,
  useProjectsQuery,
} from '../../features/reference-data/hooks'
import { ApiError } from '../../lib/apiClient'
import styles from './ContributionFormPage.module.scss'

const schema = z
  .object({
    envelopeType: z.enum(['project', 'category']),
    projectId: z.string().optional(),
    categoryId: z.string().optional(),
    paymentChannelId: z.string().min(1, 'Choisissez un canal de paiement'),
    amount: z
      .string()
      .min(1)
      .refine((v) => Number(v) > 0, { message: 'Le montant doit être supérieur à 0' }),
    periodMonth: z.string().min(1),
    transactionDate: z.string().min(1),
    donorId: z.string().optional(),
    anonymous: z.boolean(),
    notes: z.string().optional(),
    nature: z.string().min(1),
    activityId: z.string().optional(),
    remark: z.string().optional(),
  })
  .refine((v) => (v.envelopeType === 'project' ? !!v.projectId : !!v.categoryId), {
    message: 'Sélectionnez une collecte ou la caisse',
    path: ['projectId'],
  })
  .refine((v) => v.nature !== 'MONTHLY_CONTRIBUTION' || v.envelopeType === 'category', {
    message: 'Une contribution mensuelle va toujours dans la caisse du MLA',
    path: ['nature'],
  })

type FormValues = z.infer<typeof schema>

const today = new Date().toISOString().slice(0, 10)
const currentMonth = today.slice(0, 7)

export function ContributionFormPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: projects } = useProjectsQuery()
  const { data: categories } = useCategoriesQuery()
  const { data: channels } = usePaymentChannelsQuery()
  const { data: activities } = useActivitiesQuery()
  const createMutation = useCreateContributionMutation()

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      envelopeType: 'category',
      transactionDate: today,
      periodMonth: currentMonth,
      anonymous: false,
      nature: 'MONTHLY_CONTRIBUTION',
    },
  })

  const envelopeType = watch('envelopeType')
  const anonymous = watch('anonymous')
  const nature = watch('nature')
  const categoryId = watch('categoryId')

  // La caisse du MLA est l'enveloppe par défaut (contributions mensuelles) ; une collecte reçoit
  // des contributions ponctuelles, jamais de contribution mensuelle.
  const mainFund = categories?.find((c) => c.mainFund)
  useEffect(() => {
    if (mainFund && !categoryId) setValue('categoryId', String(mainFund.id))
  }, [mainFund, categoryId, setValue])
  useEffect(() => {
    if (envelopeType === 'project' && nature === 'MONTHLY_CONTRIBUTION') setValue('nature', 'CONTRIBUTION')
  }, [envelopeType, nature, setValue])
  const [submitError, setSubmitError] = useState<string | null>(null)

  const activeProjects = useMemo(() => projects?.filter((p) => p.status === 'ACTIVE') ?? [], [projects])

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    try {
      const created = await createMutation.mutateAsync({
        projectId: values.envelopeType === 'project' ? Number(values.projectId) : undefined,
        categoryId: values.envelopeType === 'category' ? Number(values.categoryId) : undefined,
        paymentChannelId: Number(values.paymentChannelId),
        amount: Number(values.amount),
        periodMonth: values.periodMonth,
        transactionDate: values.transactionDate,
        donorId: values.anonymous || !values.donorId ? undefined : Number(values.donorId),
        anonymous: values.anonymous,
        notes: values.notes || undefined,
        nature: values.nature as MovementNature,
        activityId:
          values.envelopeType === 'category' && values.activityId ? Number(values.activityId) : undefined,
        remark: values.remark || undefined,
      })
      navigate(`/contributions/${created.id}`)
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : t('contributions.form.genericError'))
    }
  })

  const envelopeButton = (type: FormValues['envelopeType'], label: string) => (
    <button
      type="button"
      className={envelopeType === type ? ui.segActive : undefined}
      aria-pressed={envelopeType === type}
      onClick={() => setValue('envelopeType', type)}
    >
      {label}
    </button>
  )

  return (
    <section>
      <Link to="/contributions" className={ui.backLink}>
        <Icon name="arrowLeft" size={15} />
        {t('contributions.detail.back')}
      </Link>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('contributions.form.title')}</h1>
          <p>{t('contributions.form.subtitle')}</p>
        </div>
      </header>

      <form className={`${ui.card} ${styles.form}`} onSubmit={onSubmit} noValidate>
        <div className={ui.formGrid}>
          <div className={`${ui.field} ${ui.span2}`}>
            <span>{t('contributions.form.envelope')}</span>
            <div className={ui.seg} role="group" aria-label={t('contributions.form.envelope')}>
              {envelopeButton('category', t('ledger.mainFundOption'))}
              {envelopeButton('project', t('ledger.collectionOption'))}
            </div>
            <span className={ui.hint}>{t('ledger.envelopeHint')}</span>
          </div>

          {envelopeType === 'project' ? (
            <label className={`${ui.field} ${ui.span2}`}>
              {t('ledger.collection')}
              <select {...register('projectId')}>
                <option value="">{t('contributions.form.selectPlaceholder')}</option>
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
                {t('contributions.form.category')}
                <select {...register('categoryId')}>
                  <option value="">{t('contributions.form.selectPlaceholder')}</option>
                  {categories?.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </select>
                {errors.projectId && <span className={ui.error}>{errors.projectId.message}</span>}
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
                <span className={ui.hint}>{t('ledger.activityHintInflow')}</span>
              </label>
            </>
          )}

          <label className={`${ui.field} ${ui.span2}`}>
            {t('ledger.nature')}
            <select {...register('nature')}>
              {INFLOW_NATURES.filter((n) => envelopeType === 'category' || n !== 'MONTHLY_CONTRIBUTION').map(
                (n) => (
                  <option key={n} value={n}>
                    {t(`nature.${n}`)}
                  </option>
                ),
              )}
            </select>
            {errors.nature && <span className={ui.error}>{errors.nature.message}</span>}
          </label>

          <label className={ui.field}>
            {t('contributions.form.amount')}
            <input type="number" inputMode="decimal" min="0" step="1" {...register('amount')} />
            {errors.amount && <span className={ui.error}>{t('contributions.form.amountError')}</span>}
          </label>

          <label className={ui.field}>
            {t('contributions.form.channel')}
            <select {...register('paymentChannelId')}>
              <option value="">{t('contributions.form.selectPlaceholder')}</option>
              {channels?.map((channel) => (
                <option key={channel.id} value={channel.id}>
                  {channel.label}
                </option>
              ))}
            </select>
            {errors.paymentChannelId && <span className={ui.error}>{errors.paymentChannelId.message}</span>}
          </label>

          <label className={ui.field}>
            {t('contributions.form.periodMonth')}
            <input type="month" {...register('periodMonth')} />
            {nature === 'MONTHLY_CONTRIBUTION' && <span className={ui.hint}>{t('ledger.periodHint')}</span>}
          </label>

          <label className={ui.field}>
            {t('contributions.form.transactionDate')}
            <input type="date" {...register('transactionDate')} />
          </label>

          <div className={`${ui.field} ${ui.span2}`}>
            <span>{t('contributions.form.donor')}</span>
            <PersonPicker
              label={t('contributions.form.donor')}
              registration={register('donorId')}
              disabled={anonymous}
              emptyLabel={t('contributions.form.noDonor')}
            />
            <label className={ui.checkRow}>
              <input type="checkbox" {...register('anonymous')} />
              {t('contributions.form.anonymous')}
            </label>
          </div>

          <label className={`${ui.field} ${ui.span2}`}>
            {t('contributions.form.notes')}
            <textarea
              rows={2}
              placeholder={t('contributions.form.notesPlaceholder')}
              {...register('notes')}
            />
          </label>

          <label className={`${ui.field} ${ui.span2}`}>
            {t('ledger.remark')}
            <input placeholder={t('ledger.remarkPlaceholder')} {...register('remark')} />
          </label>
        </div>

        <div className={`${ui.banner} ${ui.bannerNavy}`}>
          <Icon name="clock" size={17} />
          <div>{t('contributions.form.autoNotice')}</div>
        </div>

        {submitError && (
          <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
            <Icon name="alert" size={17} />
            <div>{submitError}</div>
          </div>
        )}

        <div className={`${ui.actions} ${styles.formActions}`}>
          <Link to="/contributions" className={`${ui.btn} ${ui.btnSecondary}`}>
            {t('contributions.form.cancel')}
          </Link>
          <button type="submit" className={`${ui.btn} ${ui.btnPrimary}`} disabled={isSubmitting}>
            {t('contributions.form.submit')}
          </button>
        </div>
      </form>
    </section>
  )
}
