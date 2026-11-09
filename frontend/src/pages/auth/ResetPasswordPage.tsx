import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { IccLogo } from '../../components/brand/IccLogo'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useResetPasswordMutation } from '../../features/auth/hooks'
import { ApiError } from '../../lib/apiClient'
import { useAuthStore } from '../../store/authStore'
import styles from './AuthPage.module.scss'
import { PasswordSetForm } from './PasswordSetForm'

/** Consomme le jeton de réinitialisation (?token=...) — envoyé soit via "mot de passe oublié"
 *  (ForgotPasswordPage), soit déclenché par un super admin depuis Administration → Utilisateurs. */
export function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const setSession = useAuthStore((state) => state.setSession)
  const mutation = useResetPasswordMutation()
  const [error, setError] = useState<string | null>(null)

  const onSubmit = async (password: string) => {
    if (!token) return
    setError(null)
    try {
      const response = await mutation.mutateAsync({ token, password })
      setSession(response.token, response.user)
      navigate('/')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('auth.genericError'))
    }
  }

  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <div className={styles.crest}>
          <IccLogo />
        </div>
        <div>
          <h1>{t('auth.reset.title')}</h1>
          <p className={styles.lead}>{t('auth.reset.lead')}</p>
        </div>

        {!token ? (
          <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
            <Icon name="alert" size={17} />
            <div>{t('auth.reset.missingToken')}</div>
          </div>
        ) : (
          <PasswordSetForm submitLabel={t('auth.reset.submit')} onSubmit={onSubmit} submitError={error} />
        )}
      </div>
    </main>
  )
}
