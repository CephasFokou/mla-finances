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
