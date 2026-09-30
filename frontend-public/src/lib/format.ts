// Formatage minimal : ce site n'affiche que des montants (voir frontend/src/lib/format.ts pour la
// version complète côté back-office, avec dates/mois — inutiles ici).
const amountFormatter = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 })

export function formatFcfa(amount: number): string {
  return `${amountFormatter.format(amount)} FCFA`
}
