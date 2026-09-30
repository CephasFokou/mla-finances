import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useCategoriesQuery, useProjectsQuery } from '../../features/reference-data/hooks'
import { useCreateWithdrawalMutation } from '../../features/withdrawals/hooks'
import { ApiError } from '../../lib/apiClient'
import styles from './WithdrawalFormPage.module.scss'

const schema = z
  .object({
    envelopeType: z.enum(['project', 'category']),
    projectId: z.string().optional(),
    categoryId: z.string().optional(),
    amount: z
      .string()
      .min(1)
      .refine((v) => Number(v) > 0, { message: 'Le montant doit être supérieur à 0' }),
    reason: z.string().min(1, 'Le motif est requis'),
  })
  .refine((v) => (v.envelopeType === 'project' ? !!v.projectId : !!v.categoryId), {
    message: 'Sélectionnez un projet ou une catégorie',
    path: ['projectId'],
  })

type FormValues = z.infer<typeof schema>

export function WithdrawalFormPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: projects } = useProjectsQuery()
  const { data: categories } = useCategoriesQuery()
  const createMutation = useCreateWithdrawalMutation()
  const [submitError, setSubmitError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { envelopeType: 'project' },
  })

  const envelopeType = watch('envelopeType')

  // Une demande de retrait n'a de sens que sur un projet réellement en cours de collecte.
  const activeProjects = useMemo(() => projects?.filter((p) => p.status === 'ACTIVE') ?? [], [projects])

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    try {
      const created = await createMutation.mutateAsync({
        projectId: values.envelopeType === 'project' ? Number(values.projectId) : undefined,
        categoryId: values.envelopeType === 'category' ? Number(values.categoryId) : undefined,
        amount: Number(values.amount),
        reason: values.reason,
      })
      navigate(`/withdrawals/${created.id}`)
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : t('withdrawals.form.genericError'))
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
          <div className={`${ui.field} ${ui.span2}`}>
            <span>{t('withdrawals.form.envelope')}</span>
            <div className={ui.seg} role="group" aria-label={t('withdrawals.form.envelope')}>
              {envelopeButton('project', t('withdrawals.form.projectOption'))}
              {envelopeButton('category', t('withdrawals.form.categoryOption'))}
            </div>
            <span className={ui.hint}>{t('withdrawals.form.envelopeHint')}</span>
          </div>

          {envelopeType === 'project' ? (
            <label className={`${ui.field} ${ui.span2}`}>
              {t('withdrawals.form.project')}
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
            <label className={`${ui.field} ${ui.span2}`}>
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
          )}

          <label className={ui.field}>
            {t('withdrawals.form.amount')}
            <input type="number" inputMode="decimal" min="0" step="1" {...register('amount')} />
            {errors.amount && <span className={ui.error}>{t('withdrawals.form.amountError')}</span>}
          </label>

          <label className={`${ui.field} ${ui.span2}`}>
            {t('withdrawals.form.reason')}
            <textarea
              rows={2}
              placeholder={t('withdrawals.form.reasonPlaceholder')}
              {...register('reason')}
            />
            {errors.reason && <span className={ui.error}>{t('withdrawals.form.reasonError')}</span>}
          </label>
        </div>

        <div className={`${ui.banner} ${ui.bannerNavy}`}>
          <Icon name="clock" size={17} />
          <div>{t('withdrawals.form.workflowNotice')}</div>
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
            {t('withdrawals.form.submit')}
          </button>
        </div>
      </form>
    </section>
  )
}
