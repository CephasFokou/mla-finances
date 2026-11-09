import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { IccLogo } from '../../components/brand/IccLogo'
import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import { useActivateAccountMutation } from '../../features/auth/hooks'
import { ApiError } from '../../lib/apiClient'
import { useAuthStore } from '../../store/authStore'
import styles from './AuthPage.module.scss'
import { PasswordSetForm } from './PasswordSetForm'

/** Consomme le jeton d'invitation (?token=...) envoyé par email à la création du compte (voir
 *  UserService.invite côté backend) pour fixer le premier mot de passe. */
export function ActivateAccountPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const setSession = useAuthStore((state) => state.setSession)
  const mutation = useActivateAccountMutation()
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
          <h1>{t('auth.activate.title')}</h1>
          <p className={styles.lead}>{t('auth.activate.lead')}</p>
        </div>

        {!token ? (
          <div className={`${ui.banner} ${ui.bannerRed}`} role="alert">
            <Icon name="alert" size={17} />
            <div>{t('auth.activate.missingToken')}</div>
          </div>
        ) : (
          <PasswordSetForm submitLabel={t('auth.activate.submit')} onSubmit={onSubmit} submitError={error} />
        )}
      </div>
    </main>
  )
}
