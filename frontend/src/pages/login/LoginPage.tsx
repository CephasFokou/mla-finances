import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'

import styles from './LoginPage.module.scss'

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
})

type LoginFormValues = z.infer<typeof loginSchema>

/**
 * Écran de connexion, gabarit pour les formulaires du reste de l'application :
 * React Hook Form + résolveur Zod, styles en SCSS Module colocalisé, zéro texte en dur
 * (react-i18next). La soumission réelle sera branchée sur /api/v1/auth une fois le
 * fournisseur d'identité choisi (voir backend/security/SecurityConfig).
 */
export function LoginPage() {
  const { t } = useTranslation()
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) })

  const onSubmit = handleSubmit((values) => {
    console.log('login submit (placeholder)', values)
  })

  return (
    <main className={styles.page}>
      <form className={styles.card} onSubmit={onSubmit} noValidate>
        <h1>{t('login.title')}</h1>

        <label className={styles.field} htmlFor="email">
          {t('login.email')}
          <input id="email" type="email" autoComplete="email" {...register('email')} />
          {errors.email && <span className={styles.error}>{errors.email.message}</span>}
        </label>

        <label className={styles.field} htmlFor="password">
          {t('login.password')}
          <input id="password" type="password" autoComplete="current-password" {...register('password')} />
          {errors.password && <span className={styles.error}>{errors.password.message}</span>}
        </label>

        <button className={styles.submit} type="submit" disabled={isSubmitting}>
          {t('login.submit')}
        </button>
      </form>
    </main>
  )
}
