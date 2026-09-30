import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  usePublicPaymentChannelsQuery,
  usePublicProjectsQuery,
  usePublicStatsQuery,
  useSubmitPublicDonationMutation,
} from '../features/public/hooks'
import { ApiError } from '../lib/apiClient'
import { formatFcfa } from '../lib/format'
import styles from './PublicDonationPage.module.scss'

const AMOUNT_PRESETS = [5000, 15000, 30000, 50000]
const DEFAULT_AMOUNT = AMOUNT_PRESETS[0] ?? 5000
const FAQ_KEYS = ['confirm', 'anonymous', 'channels', 'usage'] as const

/**
 * Front-office public — page « Faire un don ». Porte l'identité visuelle validée par Cephas
 * (artifact « Un don, un toit ») sur des données réelles (projets actifs, canaux de paiement) et
 * un vrai flux de soumission. Standalone : ne passe pas par AppLayout/RequireAuth, et n'a pas
 * accès aux mêmes tokens de design que le back-office (voir le module.scss, palette dédiée).
 * <p>
 * Chaque don soumis crée une PaymentTransaction PENDING, jamais une Contribution directement : le
 * secrétariat doit confirmer manuellement la réception réelle du paiement (voir
 * claude/module-front-office-public-spec.md) — il n'y a pas encore de fournisseur de paiement
 * (PawaPay) branché.
 */
export function PublicDonationPage() {
  const { t } = useTranslation()
  const { data: projects, isLoading: projectsLoading } = usePublicProjectsQuery()
  const { data: channels } = usePublicPaymentChannelsQuery()
  const { data: stats } = usePublicStatsQuery()
  const donateMutation = useSubmitPublicDonationMutation()

  const [projectId, setProjectId] = useState<number | ''>('')
  const [amount, setAmount] = useState<number>(DEFAULT_AMOUNT)
  const [customAmount, setCustomAmount] = useState('')
  const [useCustomAmount, setUseCustomAmount] = useState(false)
  const [anonymous, setAnonymous] = useState(false)
  const [donorName, setDonorName] = useState('')
  const [donorContact, setDonorContact] = useState('')
  const [channelId, setChannelId] = useState<number | ''>('')
  const [formError, setFormError] = useState<string | null>(null)
  const [openFaq, setOpenFaq] = useState<(typeof FAQ_KEYS)[number] | null>(FAQ_KEYS[0])

  // Pas d'effet : le premier projet / canal actif sert de valeur par défaut tant que le
  // donateur n'a rien choisi lui-même, dérivée directement du rendu plutôt que via setState.
  const effectiveProjectId = projectId !== '' ? projectId : (projects?.[0]?.id ?? '')
  const effectiveChannelId = channelId !== '' ? channelId : (channels?.[0]?.id ?? '')

  const effectiveAmount = useCustomAmount ? Number(customAmount) || 0 : amount
  const selectedProject = useMemo(
    () => projects?.find((p) => p.id === effectiveProjectId),
    [projects, effectiveProjectId],
  )
  const selectedChannel = useMemo(
    () => channels?.find((c) => c.id === effectiveChannelId),
    [channels, effectiveChannelId],
  )

  const handleSubmit = async () => {
    setFormError(null)
    if (!effectiveProjectId || !effectiveChannelId || effectiveAmount <= 0) {
      setFormError(t('public.form.missingFields'))
      return
    }
    if (!anonymous && !donorName.trim()) {
      setFormError(t('public.form.donorNameRequired'))
      return
    }
    try {
      await donateMutation.mutateAsync({
        projectId: effectiveProjectId,
        paymentChannelId: effectiveChannelId,
        amount: effectiveAmount,
        anonymous,
        donorName: anonymous ? undefined : donorName.trim(),
        donorContact: anonymous ? undefined : donorContact.trim() || undefined,
      })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : t('public.form.missingFields'))
    }
  }

  const resetForm = () => {
    donateMutation.reset()
    setDonorName('')
    setDonorContact('')
    setAnonymous(false)
    setUseCustomAmount(false)
    setAmount(DEFAULT_AMOUNT)
  }

  const scrollToDon = (e: React.MouseEvent<HTMLAnchorElement>) => {
    e.preventDefault()
    document.getElementById('don')?.scrollIntoView({ behavior: 'smooth' })
  }

  return (
    <div className={styles.page}>
      <header className={styles.topnav}>
        <div className={styles.navRow}>
          <a href="#" className={styles.brand}>
            <span className={styles.brandCluster}>
              <span className={styles.c1} />
              <span className={styles.c2} />
              <span className={styles.c3} />
              <span className={styles.c4} />
            </span>
            MLA
          </a>
          <nav className={styles.navLinks}>
            <a href="#projets">{t('public.nav.projects')}</a>
            <a href="#faq">{t('public.nav.faq')}</a>
          </nav>
          <a href="#don" className={styles.navCta} onClick={scrollToDon}>
            {t('public.nav.cta')}
          </a>
        </div>
      </header>

      <section className={styles.hero}>
        <div className={styles.heroWrap}>
          <span className={styles.eyebrow}>{t('public.hero.eyebrow')}</span>
          <h1 className={styles.heroTitle}>{t('public.hero.title')}</h1>
          <div className={styles.heroDot} />
          <p className={styles.heroLede}>{t('public.hero.lede')}</p>
          <div className={styles.heroActions}>
            <a href="#don" className={styles.btnGold} onClick={scrollToDon}>
              {t('public.hero.ctaMain')}
            </a>
            <a href="#projets" className={styles.btnGhost}>
              {t('public.hero.ctaSecondary')}
            </a>
          </div>
          <p className={styles.heroStats}>
            {stats
              ? t('public.hero.stats', {
                  raised: formatFcfa(stats.totalRaised),
                  count: stats.contributionCount,
                  active: stats.activeProjectCount,
                })
              : t('public.hero.statsLoading')}
          </p>
        </div>
      </section>

      <section className={styles.trustSec}>
        <h2 className={styles.trustTitle}>{t('public.trust.title')}</h2>
        <div className={styles.trustGrid}>
          {(['transparency', 'receipt', 'verification', 'channels'] as const).map((key) => (
            <div className={styles.trustItem} key={key}>
              <h4>{t(`public.trust.items.${key}.title`)}</h4>
              <p>{t(`public.trust.items.${key}.desc`)}</p>
            </div>
          ))}
        </div>
      </section>

      <section className={styles.projectsSec} id="projets">
        <span className={styles.eyebrowDark}>{t('public.projects.eyebrow')}</span>
        <h2 className={styles.sectionTitleDark}>{t('public.projects.title')}</h2>

        {projectsLoading && <p className={styles.mutedDark}>{t('public.hero.statsLoading')}</p>}
        {!projectsLoading && (!projects || projects.length === 0) && (
          <p className={styles.mutedDark}>{t('public.projects.empty')}</p>
        )}

        <div className={styles.projGrid}>
          {projects?.map((project, index) => (
            <div className={`${styles.projCard} ${styles[`c${(index % 3) + 1}`]}`} key={project.id}>
              <h3>{project.name}</h3>
              {project.description && <p className={styles.projDesc}>{project.description}</p>}
              {project.progressPercent !== null ? (
                <>
                  <div className={styles.barTrack}>
                    <div className={styles.barFill} style={{ width: `${project.progressPercent}%` }} />
                  </div>
                  <div className={styles.projAmounts}>
                    {t('public.projects.targetLine', {
                      collected: formatFcfa(project.currentBalance),
                      target: formatFcfa(project.targetAmount ?? 0),
                    })}{' '}
                    · {project.progressPercent}%
                  </div>
                </>
              ) : (
                <div className={styles.projAmounts}>{t('public.projects.noTarget')}</div>
              )}
              <a
                href="#don"
                className={styles.projMore}
                onClick={(e) => {
                  scrollToDon(e)
                  setProjectId(project.id)
                }}
              >
                {t('public.projects.support')} →
              </a>
            </div>
          ))}
        </div>
      </section>

      <section className={styles.donSec} id="don">
        <div className={styles.donHead}>
          <span className={styles.eyebrow}>{t('public.form.eyebrow')}</span>
          <h2 className={styles.sectionTitle}>{t('public.form.title')}</h2>
          <p className={styles.muted}>{t('public.form.lede')}</p>
        </div>

        <div className={styles.giveShell}>
          <div>
            <div className={styles.fieldBlock}>
              <h3>
                <span className={styles.stepNum}>1</span>
                {t('public.form.step1Title')}
              </h3>
              <select
                className={styles.select}
                value={effectiveProjectId}
                onChange={(e) => setProjectId(e.target.value ? Number(e.target.value) : '')}
              >
                <option value="" disabled>
                  {t('public.form.projectPlaceholder')}
                </option>
                {projects?.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.name}
                  </option>
                ))}
              </select>
            </div>

            <div className={styles.fieldBlock}>
              <h3>
                <span className={styles.stepNum}>2</span>
                {t('public.form.step2Title')}
              </h3>
              <div className={styles.amountGrid}>
                {AMOUNT_PRESETS.map((preset) => (
                  <button
                    key={preset}
                    type="button"
                    className={
                      !useCustomAmount && amount === preset
                        ? `${styles.chip} ${styles.chipSelected}`
                        : styles.chip
                    }
                    onClick={() => {
                      setUseCustomAmount(false)
                      setAmount(preset)
                    }}
                  >
                    {formatFcfa(preset)}
                  </button>
                ))}
                <button
                  type="button"
                  className={useCustomAmount ? `${styles.chip} ${styles.chipSelected}` : styles.chip}
                  onClick={() => setUseCustomAmount(true)}
                >
                  {t('public.form.customAmountLabel')}
                </button>
              </div>
              {useCustomAmount && (
                <div className={styles.customWrap}>
                  <input
                    type="number"
                    min={500}
                    placeholder={t('public.form.customAmountPlaceholder')}
                    value={customAmount}
                    onChange={(e) => setCustomAmount(e.target.value)}
                  />
                </div>
              )}

              <div className={styles.anonRow}>
                <div>
                  <div className={styles.anonLabel}>{t('public.form.anonymousLabel')}</div>
                  <div className={styles.anonHint}>{t('public.form.anonymousHint')}</div>
                </div>
                <button
                  type="button"
                  role="switch"
                  aria-checked={anonymous}
                  className={anonymous ? `${styles.switch} ${styles.switchOn}` : styles.switch}
                  onClick={() => setAnonymous((v) => !v)}
                >
                  <span className={styles.knob} />
                </button>
              </div>

              {!anonymous && (
                <div className={styles.donorFields}>
                  <input
                    type="text"
                    placeholder={t('public.form.donorNameLabel')}
                    value={donorName}
                    onChange={(e) => setDonorName(e.target.value)}
                  />
                  <input
                    type="text"
                    placeholder={t('public.form.donorContactLabel')}
                    value={donorContact}
                    onChange={(e) => setDonorContact(e.target.value)}
                  />
                </div>
              )}
            </div>

            <div className={styles.fieldBlock}>
              <h3>
                <span className={styles.stepNum}>3</span>
                {t('public.form.step3Title')}
              </h3>
              <div className={styles.channelGrid}>
                {channels?.map((channel) => (
                  <button
                    key={channel.id}
                    type="button"
                    className={
                      effectiveChannelId === channel.id
                        ? `${styles.channelCard} ${styles.channelSelected}`
                        : styles.channelCard
                    }
                    onClick={() => setChannelId(channel.id)}
                  >
                    {channel.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div>
            {!donateMutation.isSuccess && (
              <div className={styles.summaryPanel}>
                <h3>{t('public.form.summary.title')}</h3>
                <div className={styles.sumLine}>
                  <span>{t('public.form.summary.project')}</span>
                  <span>{selectedProject?.name ?? '—'}</span>
                </div>
                <div className={styles.sumLine}>
                  <span>{t('public.form.summary.channel')}</span>
                  <span>{selectedChannel?.label ?? '—'}</span>
                </div>
                <div className={styles.sumLine}>
                  <span>{t('public.form.summary.donor')}</span>
                  <span>
                    {anonymous
                      ? t('public.form.summary.donorAnonymous')
                      : t('public.form.summary.donorVisible')}
                  </span>
                </div>
                <div className={styles.sumTotal}>
                  <span>{t('public.form.summary.amount')}</span>
                  <span className={styles.sumVal}>{formatFcfa(effectiveAmount)}</span>
                </div>
                {formError && <div className={styles.formError}>{formError}</div>}
                <button
                  type="button"
                  className={styles.submitBtn}
                  disabled={donateMutation.isPending}
                  onClick={() => void handleSubmit()}
                >
                  {donateMutation.isPending
                    ? t('public.form.submitting')
                    : t('public.form.submit', { amount: formatFcfa(effectiveAmount) })}
                </button>
                <div className={styles.secureNote}>{t('public.form.secureNote')}</div>
              </div>
            )}

            {donateMutation.isSuccess && (
              <div className={styles.successPanel}>
                <h3>{t('public.success.title')}</h3>
                <p>
                  {t('public.success.lede')} {selectedProject?.name}
                </p>
                <div className={styles.refBox}>
                  <div>
                    {t('public.success.referenceLabel')}
                    <b>{donateMutation.data?.reference}</b>
                  </div>
                </div>
                <p className={styles.pendingNote}>{t('public.success.statusPending')}</p>
                <button type="button" className={styles.btnGhostDark} onClick={resetForm}>
                  {t('public.success.newDonation')}
                </button>
              </div>
            )}
          </div>
        </div>
      </section>

      <section className={styles.faqSec} id="faq">
        <div className={styles.faqGrid}>
          <div>
            <span className={styles.eyebrow}>{t('public.faq.eyebrow')}</span>
            <h2 className={styles.sectionTitle}>{t('public.faq.title')}</h2>
          </div>
          <div>
            {FAQ_KEYS.map((key) => (
              <div
                className={openFaq === key ? `${styles.faqItem} ${styles.faqOpen}` : styles.faqItem}
                key={key}
              >
                <button
                  type="button"
                  className={styles.faqQ}
                  onClick={() => setOpenFaq((current) => (current === key ? null : key))}
                >
                  {t(`public.faq.items.${key}.q`)}
                  <span className={styles.faqPlus}>+</span>
                </button>
                <div className={styles.faqA}>{t(`public.faq.items.${key}.a`)}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <footer className={styles.footer}>
        <div className={styles.footMain}>
          <div>
            <div className={styles.footBrand}>
              <span className={styles.brandCluster}>
                <span className={styles.c1} />
                <span className={styles.c2} />
                <span className={styles.c3} />
                <span className={styles.c4} />
              </span>
              MLA
            </div>
            <p className={styles.footTagline}>{t('public.footer.tagline')}</p>
          </div>
          <div>
            <h4>{t('public.footer.navTitle')}</h4>
            <ul className={styles.footLinks}>
              <li>
                <a href="#projets">{t('public.nav.projects')}</a>
              </li>
              <li>
                <a href="#don">{t('public.nav.cta')}</a>
              </li>
              <li>
                <a href="#faq">{t('public.nav.faq')}</a>
              </li>
            </ul>
          </div>
          <div>
            <h4>{t('public.footer.helpTitle')}</h4>
            <p className={styles.footTagline}>{t('public.footer.contactNote')}</p>
          </div>
        </div>
        <div className={styles.footBottom}>
          <span>© {new Date().getFullYear()} MLA</span>
          <span>{t('public.footer.rights')}</span>
        </div>
      </footer>
    </div>
  )
}
