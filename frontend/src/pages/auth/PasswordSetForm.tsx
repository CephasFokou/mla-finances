import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import styles from './AuthPage.module.scss'

const schema = z
  .object({
    password: z.string().min(8),
    confirmPassword: z.string().min(8),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'passwordMismatch',
  })

type FormValues = z.infer<typeof schema>

/**
 * Formulaire "choisir un mot de passe", partagé par ActivateAccountPage (jeton d'invitation) et
 * ResetPasswordPage (jeton de réinitialisation) — même UI, seul le endpoint appelé par le parent
 * diffère (voir la javadoc d'AuthService côté backend pour pourquoi ce sont deux jetons distincts
 * plutôt qu'un seul parcours unifié).
 */
export function PasswordSetForm({
  submitLabel,
  onSubmit,
  submitError,
}: {
  submitLabel: string
  onSubmit: (password: string) => Promise<void>
  submitError: string | null
}) {
  const { t } = useTranslation()
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const submit = handleSubmit(async (values) => {
    await onSubmit(values.password)
  })

  return (
    <form onSubmit={(e) => void submit(e)} noValidate>
      <label className={ui.field} htmlFor="password">
        {t('auth.newPassword')}
        <input id="password" type="password" autoComplete="new-password" {...register('password')} />
        {errors.password && <span className={ui.error}>{t('auth.passwordTooShort')}</span>}
      </label>

      <label className={ui.field} htmlFor="confirmPassword">
        {t('auth.confirmPassword')}
        <input
          id="confirmPassword"
          type="password"
          autoComplete="new-password"
          {...register('confirmPassword')}
        />
        {errors.confirmPassword && <span className={ui.error}>{t('auth.passwordMismatch')}</span>}
      </label>

      {submitError && (
        <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
          <Icon name="alert" size={17} />
          <div>{submitError}</div>
        </div>
      )}

      <button className={`${ui.btn} ${ui.btnPrimary} ${styles.submit}`} type="submit" disabled={isSubmitting}>
        {submitLabel}
      </button>
    </form>
  )
}
