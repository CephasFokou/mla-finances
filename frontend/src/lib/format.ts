const amountFormatter = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 })
const signedAmountFormatter = new Intl.NumberFormat('fr-FR', {
  maximumFractionDigits: 0,
  signDisplay: 'exceptZero',
})
const dateFormatter = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' })
const monthFormatter = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' })
const dateTimeFormatter = new Intl.DateTimeFormat('fr-FR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatFcfa(amount: number): string {
  return `${amountFormatter.format(amount)} FCFA`
}

/** Montant avec signe explicite (+25 000 / −180 000), pour les listes de mouvements. */
export function formatSignedAmount(amount: number): string {
  return signedAmountFormatter.format(amount).replace('-', '−')
}

export function formatDate(isoDate: string): string {
  return dateFormatter.format(new Date(isoDate))
}

/** « septembre 2026 » à partir d'une date normalisée au 1er du mois. */
export function formatMonth(isoDate: string): string {
  return monthFormatter.format(new Date(isoDate))
}

export function formatDateTime(isoInstant: string): string {
  return dateTimeFormatter.format(new Date(isoInstant))
}

const percentFormatter = new Intl.NumberFormat('fr-FR', { style: 'percent', maximumFractionDigits: 0 })
const shortMonthFormatter = new Intl.DateTimeFormat('fr-FR', { month: 'short' })

/** « 58 % » à partir d'une part entre 0 et 1 ; tiret si la part n'existe pas (total nul). */
export function formatPercent(share: number | null | undefined): string {
  return share === null || share === undefined ? '—' : percentFormatter.format(share)
}

/** Montant sans unité, tiret pour zéro : lecture rapide d'un tableau dense (calendrier, synthèse). */
export function formatAmountCell(amount: number): string {
  return amount === 0 ? '–' : amountFormatter.format(amount)
}

/** « mars » à partir de « 2026-03 » (mois d'attribution). */
export function formatMonthKey(monthKey: string): string {
  return shortMonthFormatter.format(new Date(`${monthKey}-01T12:00:00`))
}

/** Nom à afficher pour une personne : nom usuel du journal, sinon nom complet. */
export function personLabel(
  person: { displayName?: string | null; fullName?: string | null } | null | undefined,
): string {
  return person?.displayName || person?.fullName || '—'
}
