import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useInviteUserMutation, useRolesQuery } from '../../features/administration/hooks'
import { ApiError } from '../../lib/apiClient'
import styles from './AdministrationPage.module.scss'

const schema = z.object({
  email: z.string().min(1, "L'email est requis").email('Adresse email invalide'),
  fullName: z.string().min(1, 'Le nom complet est requis'),
  roleIds: z.array(z.number()).min(1, 'Sélectionnez au moins un rôle'),
})

type FormValues = z.infer<typeof schema>

export function UserInvitePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: roles } = useRolesQuery()
  const inviteMutation = useInviteUserMutation()
  const [submitError, setSubmitError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { roleIds: [] },
  })

  const selectedRoleIds = watch('roleIds')

  const toggleRole = (roleId: number, checked: boolean) => {
    const next = checked ? [...selectedRoleIds, roleId] : selectedRoleIds.filter((id) => id !== roleId)
    setValue('roleIds', next, { shouldValidate: true })
  }

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    try {
      await inviteMutation.mutateAsync(values)
      navigate('/administration')
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : t('administration.users.genericError'))
    }
  })

  return (
    <section>
      <Link to="/administration" className={ui.backLink}>
        <Icon name="arrowLeft" size={15} />
        {t('administration.users.back')}
      </Link>
      <header className={ui.pageHead}>
        <div>
          <h1>{t('administration.users.inviteTitle')}</h1>
          <p>{t('administration.users.inviteSubtitle')}</p>
        </div>
      </header>

      <form className={`${ui.card} ${styles.form}`} onSubmit={onSubmit} noValidate>
        <div className={ui.formGrid}>
          <label className={ui.field}>
            {t('administration.users.fullName')}
            <input type="text" {...register('fullName')} />
            {errors.fullName && <span className={ui.error}>{errors.fullName.message}</span>}
          </label>

          <label className={ui.field}>
            {t('administration.users.email')}
            <input type="email" {...register('email')} />
            {errors.email && <span className={ui.error}>{errors.email.message}</span>}
          </label>

          <div className={`${ui.field} ${ui.span2}`}>
            <span>{t('administration.users.roles')}</span>
            <div className={styles.roleChecklistGrid}>
              {roles?.map((role) => (
                <label key={role.id} className={ui.checkRow}>
                  <input
                    type="checkbox"
                    checked={selectedRoleIds.includes(role.id)}
                    onChange={(e) => toggleRole(role.id, e.target.checked)}
                  />
                  {role.name}
                  {role.description && <span className={ui.cellSub}> — {role.description}</span>}
                </label>
              ))}
            </div>
            {errors.roleIds && <span className={ui.error}>{errors.roleIds.message}</span>}
          </div>
        </div>

        <div className={`${ui.banner} ${ui.bannerNavy}`}>
          <Icon name="clock" size={17} />
          <div>{t('administration.users.inviteNotice')}</div>
        </div>

        {submitError && (
          <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
            <Icon name="alert" size={17} />
            <div>{submitError}</div>
          </div>
        )}

        <div className={`${ui.actions} ${styles.formActions}`}>
          <Link to="/administration" className={`${ui.btn} ${ui.btnSecondary}`}>
            {t('administration.users.cancel')}
          </Link>
          <button type="submit" className={`${ui.btn} ${ui.btnPrimary}`} disabled={isSubmitting}>
            {t('administration.users.sendInvitation')}
          </button>
        </div>
      </form>
    </section>
  )
}
