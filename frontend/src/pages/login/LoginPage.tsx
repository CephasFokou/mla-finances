import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useLoginMutation } from '../../features/auth/hooks'
import { ApiError } from '../../lib/apiClient'
import { useAuthStore } from '../../store/authStore'
import styles from './LoginPage.module.scss'

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
})

type LoginFormValues = z.infer<typeof loginSchema>

/**
 * Écran de connexion (mise en page « split » des maquettes), gabarit pour les formulaires du reste
 * de l'application : React Hook Form + résolveur Zod, styles en SCSS Module colocalisé, zéro texte
 * en dur (react-i18next). RG-18 : soumission réelle sur /api/v1/auth/login (voir
 * backend/security/SecurityConfig et service/AuthService) — un 401 ici (identifiants incorrects)
 * est un échec de connexion ordinaire, à ne pas confondre avec une session expirée (voir apiClient,
 * qui ne déclenche expireSession() que si un jeton était déjà attaché à la requête).
 */
export function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)
  const loginMutation = useLoginMutation()
  const [submitError, setSubmitError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) })

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    try {
      const response = await loginMutation.mutateAsync(values)
      setSession(response.token, response.user)
      navigate('/')
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : t('login.genericError'))
    }
  })

  return (
    <main className={styles.page}>
      <div className={styles.shell}>
        <aside className={styles.side}>
          <div className={styles.mark}>
            <div className={styles.crest}>
              <Icon name="crest" size={22} />
            </div>
            <div>
              <b>{t('app.name')}</b>
              <span>{t('app.church')}</span>
            </div>
          </div>
          <blockquote className={styles.quote}>
            {t('login.quote')}
            <cite>{t('login.quoteRef')}</cite>
          </blockquote>
          <p className={styles.foot}>{t('login.restricted')}</p>
        </aside>

        <form className={styles.form} onSubmit={onSubmit} noValidate>
          <div>
            <h1>{t('login.title')}</h1>
            <p className={styles.lead}>{t('login.lead')}</p>
          </div>

          <label className={ui.field} htmlFor="email">
            {t('login.email')}
            <input
              id="email"
              type="email"
              autoComplete="email"
              placeholder={t('login.emailPlaceholder')}
              {...register('email')}
            />
            {errors.email && <span className={ui.error}>{errors.email.message}</span>}
          </label>

          <label className={ui.field} htmlFor="password">
            {t('login.password')}
            <input id="password" type="password" autoComplete="current-password" {...register('password')} />
            {errors.password && <span className={ui.error}>{errors.password.message}</span>}
          </label>

          {submitError && (
            <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
              <Icon name="alert" size={17} />
              <div>{submitError}</div>
            </div>
          )}

          <button
            className={`${ui.btn} ${ui.btnPrimary} ${styles.submit}`}
            type="submit"
            disabled={isSubmitting}
          >
            {t('login.submit')}
          </button>
          <Link to="/forgot-password" className={styles.forgot}>
            {t('login.forgotPassword')}
          </Link>
          <p className={`${ui.hint} ${styles.audit}`}>{t('login.audit')}</p>
        </form>
      </div>
    </main>
  )
}
