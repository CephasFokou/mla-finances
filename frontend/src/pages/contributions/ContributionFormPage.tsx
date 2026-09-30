import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useCreateContributionMutation } from '../../features/contributions/hooks'
import {
  useCategoriesQuery,
  useDonorSearchQuery,
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
  })
  .refine((v) => (v.envelopeType === 'project' ? !!v.projectId : !!v.categoryId), {
    message: 'Sélectionnez un projet ou une catégorie',
    path: ['projectId'],
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
  const [donorQuery, setDonorQuery] = useState('')
  const { data: donors } = useDonorSearchQuery(donorQuery)
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
    },
  })

  const envelopeType = watch('envelopeType')
  const anonymous = watch('anonymous')
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
              {envelopeButton('category', t('contributions.form.categoryOption'))}
              {envelopeButton('project', t('contributions.form.projectOption'))}
            </div>
            <span className={ui.hint}>{t('contributions.form.envelopeHint')}</span>
          </div>

          {envelopeType === 'project' ? (
            <label className={`${ui.field} ${ui.span2}`}>
              {t('contributions.form.project')}
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
            <label className={`${ui.field} ${ui.span2}`}>
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
          )}

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
          </label>

          <label className={ui.field}>
            {t('contributions.form.transactionDate')}
            <input type="date" {...register('transactionDate')} />
          </label>

          <div className={`${ui.field} ${ui.span2}`}>
            <span>{t('contributions.form.donor')}</span>
            <div className={styles.donorPicker}>
              <input
                type="search"
                value={donorQuery}
                disabled={anonymous}
                onChange={(e) => setDonorQuery(e.target.value)}
                placeholder={t('contributions.form.donorSearchPlaceholder')}
                aria-label={t('contributions.form.donorSearch')}
              />
              <select
                disabled={anonymous}
                aria-label={t('contributions.form.donor')}
                {...register('donorId')}
              >
                <option value="">{t('contributions.form.noDonor')}</option>
                {donors?.map((donor) => (
                  <option key={donor.id} value={donor.id}>
                    {donor.fullName}
                  </option>
                ))}
              </select>
            </div>
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
