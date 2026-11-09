import { useMemo, useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'

import { IccLogo } from '../components/IccLogo'
import { RibbonProgress } from '../components/RibbonProgress'
import { usePublicCollectionsQuery } from '../features/public/hooks'
import type { PublicCollection } from '../features/public/types'
import { formatAmount, formatDayMonth, formatFcfa, formatPeriod } from '../lib/format'
import { DonationDialog } from './DonationDialog'
import styles from './PublicDonationPage.module.scss'

/** Couleurs des rubans du logo, attribuées aux collectes dans l'ordre (violet, bleu, rouge, vert, jaune). */
const RIBBON_COLORS = ['#70358E', '#16A5DA', '#DF3A3A', '#72BF5B', '#F9D03B']
const STEPS = ['s1', 's2', 's3'] as const

/**
 * Site public de don, charte ICC : « l'envol » du logo à l'ouverture, une collecte = un ruban de
 * couleur, et la transparence sur ce qui a été remis. Montants agrégés uniquement (aucun donateur).
 */
export function PublicDonationPage() {
  const { t } = useTranslation()
  const { data: collections, isLoading, isError } = usePublicCollectionsQuery()
  const [dialog, setDialog] = useState<{ collectionId: number | null } | null>(null)

  const { open, upcoming, closed, totalCollected, fundedCount } = useMemo(() => {
    const list = collections ?? []
    return {
      open: list.filter((c) => c.state === 'OPEN'),
      upcoming: list.filter((c) => c.state === 'UPCOMING'),
      closed: list.filter((c) => c.state === 'CLOSED'),
      totalCollected: list.reduce((sum, c) => sum + c.collected, 0),
      fundedCount: list.filter((c) => c.collected > 0).length,
    }
  }, [collections])

  const ribbonColor = (id: number) => {
    const index = (collections ?? []).findIndex((c) => c.id === id)
    return RIBBON_COLORS[(index < 0 ? id : index) % RIBBON_COLORS.length] ?? RIBBON_COLORS[0]!
  }

  const give = (collectionId: number | null) => setDialog({ collectionId })

  return (
    <div className={styles.site}>
      <header className={styles.hero}>
        <IccLogo ribbonsOnly mono="#FFFFFF" className={styles.ghost} />
        <nav className={`${styles.wrap} ${styles.topbar}`} aria-label={t('public.nav.label')}>
          <a href="#top" className={styles.brand}>
            <span className={styles.brandLogo}>
              <IccLogo letterColor="#FFFFFF" />
            </span>
            <span>
              {t('public.brand')}
              <small>{t('public.brandSub')}</small>
            </span>
          </a>
          <div className={styles.navLinks}>
            <a href="#collectes">{t('public.nav.collections')}</a>
            <a href="#donner">{t('public.nav.how')}</a>
            <a href="#transparence">{t('public.nav.impact')}</a>
          </div>
          <button type="button" className={`${styles.btn} ${styles.btnGold}`} onClick={() => give(null)}>
            {t('public.nav.cta')}
          </button>
        </nav>

        <div className={`${styles.wrap} ${styles.heroGrid}`} id="top">
          <div>
            <h1 className={styles.heroTitle}>{t('public.hero.title')}</h1>
            <p className={styles.lead}>{t('public.hero.lede')}</p>
            <div className={styles.heroActions}>
              <button
                type="button"
                className={`${styles.btn} ${styles.btnGold} ${styles.btnLg}`}
                onClick={() => give(open.length === 1 ? (open[0]?.id ?? null) : null)}
              >
                {t('public.hero.ctaMain')}
              </button>
              <a href="#transparence" className={`${styles.btn} ${styles.btnGhost} ${styles.btnLg}`}>
                {t('public.hero.ctaSecondary')}
              </a>
            </div>
            {fundedCount > 0 && (
              <p className={styles.proof}>
                <Trans
                  i18nKey="public.hero.proof"
                  count={fundedCount}
                  values={{ amount: formatFcfa(totalCollected) }}
                  components={{ b: <strong /> }}
                />
              </p>
            )}
          </div>
          <div className={styles.heroLogo}>
            <IccLogo animated letterColor="#FFFFFF" title="Impact Centre Chrétien" />
          </div>
        </div>
      </header>

      <main>
        <section
          className={`${styles.wrap} ${styles.section}`}
          id="collectes"
          aria-labelledby="collectes-title"
        >
          <div className={styles.sectionHead}>
            <h2 id="collectes-title">{t('public.collections.title')}</h2>
            <p>{t('public.collections.intro')}</p>
          </div>
          {isLoading && <p className={styles.status}>{t('public.collections.loading')}</p>}
          {isError && (
            <p className={styles.status} role="alert">
              {t('public.collections.error')}
            </p>
          )}
          {collections && open.length === 0 && upcoming.length === 0 && (
            <p className={styles.status}>{t('public.collections.empty')}</p>
          )}
          <div className={styles.collects}>
            {open.map((c) => (
              <OpenCollection key={c.id} collection={c} color={ribbonColor(c.id)} onGive={() => give(c.id)} />
            ))}
            {upcoming.map((c) => (
              <article key={c.id} className={`${styles.collect} ${styles.collectSoon}`}>
                <div className={styles.collectTop}>
                  <span className={`${styles.pill} ${styles.pillSoon}`}>{t('public.collections.soon')}</span>
                  <span className={styles.collectMeta}>{t('public.collections.soonMeta')}</span>
                </div>
                <h3>{c.name}</h3>
                <div className={`${styles.collectAmount} ${styles.muted}`}>
                  {t('public.collections.upcomingText')}
                </div>
                <RibbonProgress color={ribbonColor(c.id)} empty />
                <span className={`${styles.btn} ${styles.btnDisabled}`} aria-disabled="true">
                  {t('public.collections.opensSoon')}
                </span>
              </article>
            ))}
          </div>
        </section>

        <section className={styles.band} id="donner" aria-labelledby="donner-title">
          <div className={`${styles.wrap} ${styles.section}`}>
            <div className={styles.sectionHead}>
              <h2 id="donner-title">{t('public.how.title')}</h2>
              <p>{t('public.how.intro')}</p>
            </div>
            <ol className={styles.steps}>
              {STEPS.map((key, i) => (
                <li key={key}>
                  <span className={styles.stepN} aria-hidden="true">
                    {i + 1}
                  </span>
                  <h3>{t(`public.how.${key}.title`)}</h3>
                  <p>{t(`public.how.${key}.text`)}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section
          className={`${styles.wrap} ${styles.section}`}
          id="transparence"
          aria-labelledby="impact-title"
        >
          <div className={styles.sectionHead}>
            <h2 id="impact-title">{t('public.impact.title')}</h2>
            <p>{t('public.impact.intro')}</p>
          </div>
          {closed.length === 0 ? (
            <p className={styles.status}>{t('public.impact.empty')}</p>
          ) : (
            <div className={styles.ledgerWrap}>
              <table className={styles.ledger}>
                <thead>
                  <tr>
                    <th scope="col">
                      <span className={styles.srOnly}>{t('public.impact.collection')}</span>
                    </th>
                    <th scope="col">{t('public.impact.collection')}</th>
                    <th scope="col">{t('public.impact.received')}</th>
                    <th scope="col">{t('public.impact.handedOver')}</th>
                  </tr>
                </thead>
                <tbody>
                  {closed.map((c) => (
                    <tr key={c.id}>
                      <td>
                        <span className={styles.dot} style={{ background: ribbonColor(c.id) }} />
                      </td>
                      <td>
                        <span className={styles.ledgerName}>{c.name}</span>
                        <span className={styles.ledgerWhen}>
                          {formatPeriod(c.firstMovementDate, c.lastMovementDate)}
                        </span>
                      </td>
                      <td className={styles.num}>{formatAmount(c.collected)}</td>
                      <td className={`${styles.num} ${styles.strong}`}>{formatAmount(c.handedOver)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>

      <footer className={styles.foot}>
        <div className={`${styles.wrap} ${styles.footGrid}`}>
          <span className={styles.footLogo}>
            <IccLogo letterColor="#FFFFFF" />
          </span>
          <div>
            <strong>{t('public.footer.name')}</strong>
            <p>{t('public.footer.contact')}</p>
          </div>
        </div>
      </footer>

      {dialog && (
        <DonationDialog
          onClose={() => setDialog(null)}
          collections={open}
          ribbonColor={ribbonColor}
          initialCollectionId={dialog.collectionId}
        />
      )}
    </div>
  )
}

function OpenCollection({
  collection,
  color,
  onGive,
}: {
  collection: PublicCollection
  color: string
  onGive: () => void
}) {
  const { t } = useTranslation()
  return (
    <article className={styles.collect}>
      <div className={styles.collectTop}>
        <span className={`${styles.pill} ${styles.pillLive}`}>{t('public.collections.live')}</span>
        {collection.firstMovementDate && (
          <span className={styles.collectMeta}>
            {t('public.collections.since', { date: formatDayMonth(collection.firstMovementDate) })}
          </span>
        )}
      </div>
      <h3>{collection.name}</h3>
      <div className={styles.collectAmount}>
        {collection.contributionsCount > 0 ? (
          <>
            <span className={styles.big}>{formatAmount(collection.collected)}</span>{' '}
            {t('public.collections.received', { count: collection.contributionsCount })}
          </>
        ) : (
          t('public.collections.receivedNone')
        )}
      </div>
      <RibbonProgress color={color} empty={collection.contributionsCount === 0} />
      <button type="button" className={`${styles.btn} ${styles.btnViolet}`} onClick={onGive}>
        {t('public.collections.give')}
      </button>
    </article>
  )
}
