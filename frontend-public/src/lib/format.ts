// Formatage minimal : ce site n'affiche que des montants (voir frontend/src/lib/format.ts pour la
// version complète côté back-office, avec dates/mois — inutiles ici).
const amountFormatter = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 })

export function formatFcfa(amount: number): string {
  return `${amountFormatter.format(amount)} FCFA`
}

const monthFormatter = new Intl.DateTimeFormat('fr-FR', { month: 'long' })
const dayMonthFormatter = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long' })

/** Montant sans unité (« 24 000 »), pour les chiffres mis en avant. */
export function formatAmount(amount: number): string {
  return amountFormatter.format(amount)
}

/** « juin », « juin à juillet » : période d'une collecte à partir de ses dates extrêmes. */
export function formatPeriod(from: string | null, to: string | null): string {
  if (!from) return ''
  const a = monthFormatter.format(new Date(`${from}T12:00:00`))
  const b = to ? monthFormatter.format(new Date(`${to}T12:00:00`)) : a
  return a === b ? a : `${a} à ${b}`
}

/** « 9 octobre » */
export function formatDayMonth(date: string): string {
  return dayMonthFormatter.format(new Date(`${date}T12:00:00`))
}
