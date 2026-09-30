import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useCreateProjectMutation } from '../../features/projects/hooks'
import { ApiError } from '../../lib/apiClient'
import styles from './ProjectFormPage.module.scss'

const schema = z.object({
  name: z.string().min(1, 'Le nom du projet est requis'),
  description: z.string().optional(),
  openingBalance: z
    .string()
    .min(1)
    .refine((v) => Number(v) >= 0, { message: "Le solde d'ouverture doit être positif ou nul" }),
  targetAmount: z.string().optional(),
  recurringSeriesId: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
})

type FormValues = z.infer<typeof schema>

export function ProjectFormPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const createMutation = useCreateProjectMutation()
  const [submitError, setSubmitError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { openingBalance: '0' },
  })

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    try {
      const created = await createMutation.mutateAsync({
        name: values.name,
        description: values.description || undefined,
        openingBalance: Number(values.openingBalance),
        targetAmount: values.targetAmount ? Number(values.targetAmount) : undefined,
        recurringSeriesId: values.recurringSeriesId || undefined,
        startDate: values.startDate || undefined,
        endDate: values.endDate || undefined,
      })
      navigate(`/projects/${created.id}`)
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : t('projects.form.genericError'))
    }
  })

  return (
    <section>
      <Link to="/projects" className={ui.backLink}>
        <Icon name="arrowLeft" size={15} />
        {t('projects.detail.back')}
      </Link>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('projects.form.createTitle')}</h1>
          <p>{t('projects.form.createSubtitle')}</p>
        </div>
      </header>

      <form className={`${ui.card} ${styles.form}`} onSubmit={onSubmit} noValidate>
        <div className={ui.formGrid}>
          <label className={`${ui.field} ${ui.span2}`}>
            {t('projects.form.name')}
            <input type="text" {...register('name')} />
            {errors.name && <span className={ui.error}>{errors.name.message}</span>}
          </label>

          <label className={`${ui.field} ${ui.span2}`}>
            {t('projects.form.description')}
            <textarea
              rows={2}
              placeholder={t('projects.form.descriptionPlaceholder')}
              {...register('description')}
            />
          </label>

          <label className={ui.field}>
            {t('projects.form.openingBalance')}
            <input type="number" inputMode="decimal" min="0" step="1" {...register('openingBalance')} />
            {errors.openingBalance && <span className={ui.error}>{errors.openingBalance.message}</span>}
            <span className={ui.hint}>{t('projects.form.openingBalanceHint')}</span>
          </label>

          <label className={ui.field}>
            {t('projects.form.targetAmount')}
            <input type="number" inputMode="decimal" min="0" step="1" {...register('targetAmount')} />
          </label>

          <label className={ui.field}>
            {t('projects.form.startDate')}
            <input type="date" {...register('startDate')} />
          </label>

          <label className={ui.field}>
            {t('projects.form.endDate')}
            <input type="date" {...register('endDate')} />
          </label>

          <label className={`${ui.field} ${ui.span2}`}>
            {t('projects.form.recurringSeriesId')}
            <input type="text" placeholder="COMFRAT" {...register('recurringSeriesId')} />
            <span className={ui.hint}>{t('projects.form.recurringSeriesIdHint')}</span>
          </label>
        </div>

        {submitError && (
          <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
            <Icon name="alert" size={17} />
            <div>{submitError}</div>
          </div>
        )}

        <div className={`${ui.actions} ${styles.formActions}`}>
          <Link to="/projects" className={`${ui.btn} ${ui.btnSecondary}`}>
            {t('projects.form.cancel')}
          </Link>
          <button type="submit" className={`${ui.btn} ${ui.btnPrimary}`} disabled={isSubmitting}>
            {t('projects.form.submit')}
          </button>
        </div>
      </form>
    </section>
  )
}
