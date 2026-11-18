import i18n from '../i18n/i18n'

// Formatage minimal, dans la langue choisie (fr-FR ou en-GB) : montants, mois et jours.
function locale(): string {
  return i18n.language === 'en' ? 'en-GB' : 'fr-FR'
}

function at(date: string): Date {
  return new Date(`${date}T12:00:00`)
}

/** « 5 000 FCFA » */
export function formatFcfa(amount: number): string {
  return `${formatAmount(amount)} FCFA`
}

/** Montant sans unité (« 24 000 »), pour les chiffres mis en avant. */
export function formatAmount(amount: number): string {
  return new Intl.NumberFormat(locale(), { maximumFractionDigits: 0 }).format(amount)
}

/** « juin », « juin à juillet » : période d'une collecte à partir de ses dates extrêmes. */
export function formatPeriod(from: string | null, to: string | null): string {
  if (!from) return ''
  const month = new Intl.DateTimeFormat(locale(), { month: 'long' })
  const a = month.format(at(from))
  const b = to ? month.format(at(to)) : a
  return a === b ? a : i18n.t('public.period', { from: a, to: b })
}

/** « 9 octobre » */
export function formatDayMonth(date: string): string {
  return new Intl.DateTimeFormat(locale(), { day: 'numeric', month: 'long' }).format(at(date))
}

/** « nov. 2026 » à partir de « 2026-11 » (cases à cocher des mois). */
export function formatMonthShort(monthKey: string): string {
  return new Intl.DateTimeFormat(locale(), { month: 'short', year: 'numeric' }).format(at(`${monthKey}-01`))
}

/** « novembre et décembre 2026 », « janvier 2026, février 2026 et mars 2026 » : mois payés. */
export function formatMonthList(monthKeys: string[]): string {
  const month = new Intl.DateTimeFormat(locale(), { month: 'long', year: 'numeric' })
  const labels = [...monthKeys].sort().map((key) => month.format(at(`${key}-01`)))
  return new Intl.ListFormat(locale(), { style: 'long', type: 'conjunction' }).format(labels)
}

/** Mois courant « YYYY-MM » à l'heure du téléphone (le visiteur est au Cameroun). */
export function currentMonthKey(now: Date = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}
