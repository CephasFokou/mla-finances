import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { z } from 'zod'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useForgotPasswordMutation } from '../../features/auth/hooks'
import styles from './AuthPage.module.scss'

const schema = z.object({ email: z.string().email() })
type FormValues = z.infer<typeof schema>

/**
 * Écran public "mot de passe oublié" (libre-service, en plus de la réinitialisation déclenchée par
 * un super admin — voir claude/module-authentification-spec.md). Le backend répond toujours 204,
 * qu'un compte existe ou non pour cet email (RG implicite : pas d'énumération des comptes) : le
 * message de succès affiché ici est donc volontairement le même dans tous les cas.
 */
export function ForgotPasswordPage() {
  const { t } = useTranslation()
  const mutation = useForgotPasswordMutation()
  const [submitted, setSubmitted] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = handleSubmit(async (values) => {
    await mutation.mutateAsync(values)
    setSubmitted(true)
  })

  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <div className={styles.crest}>
          <Icon name="crest" size={20} />
        </div>
        <div>
          <h1>{t('auth.forgot.title')}</h1>
          <p className={styles.lead}>{t('auth.forgot.lead')}</p>
        </div>

        {submitted ? (
          <div className={`${ui.banner} ${ui.bannerGreen}`} role="status">
            <Icon name="check" size={17} />
            <div>{t('auth.forgot.success')}</div>
          </div>
        ) : (
          <form onSubmit={(e) => void onSubmit(e)} noValidate>
            <label className={ui.field} htmlFor="email">
              {t('login.email')}
              <input
                id="email"
                type="email"
                autoComplete="email"
                placeholder={t('login.emailPlaceholder')}
                {...register('email')}
              />
              {errors.email && <span className={ui.error}>{t('auth.forgot.invalidEmail')}</span>}
            </label>

            <button
              className={`${ui.btn} ${ui.btnPrimary} ${styles.submit}`}
              type="submit"
              disabled={isSubmitting}
            >
              {t('auth.forgot.submit')}
            </button>
          </form>
        )}

        <Link to="/login" className={`${ui.backLink} ${styles.backLink}`}>
          <Icon name="arrowLeft" size={15} />
          {t('auth.backToLogin')}
        </Link>
      </div>
    </main>
  )
}
