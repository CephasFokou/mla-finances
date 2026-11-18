import { useEffect, useState } from 'react'

import { useDonationStatusQuery } from './hooks'
import type { PublicDonationResult, PublicDonationStatus } from './types'

/** Au-delà de ce délai d'attente de la validation Mobile Money, on explique au payeur qu'il peut fermer. */
const LONG_WAIT_MS = 120_000

/** Numéro saisi : 9 chiffres, ou 12 avec l'indicatif 237. */
export function phoneDigitsOf(phone: string) {
  const digits = phone.replace(/\D/g, '')
  return { digits, valid: digits.length === 9 || (digits.length === 12 && digits.startsWith('237')) }
}

/**
 * Suivi d'un paiement en ligne tant qu'il n'est pas final : statut en direct, motif d'échec, et
 * « longue attente » au-delà de 2 minutes.
 */
export function useOnlinePaymentTracking(result: PublicDonationResult | undefined) {
  const tracking =
    result && result.mode === 'ONLINE' && result.status === 'PROCESSING' ? result.trackingToken : null
  const statusQuery = useDonationStatusQuery(tracking)
  const [longWait, setLongWait] = useState(false)

  useEffect(() => {
    if (!tracking) return
    const timer = window.setTimeout(() => setLongWait(true), LONG_WAIT_MS)
    return () => {
      window.clearTimeout(timer)
      setLongWait(false)
    }
  }, [tracking])

  const liveStatus: PublicDonationStatus | undefined = statusQuery.data?.status ?? result?.status
  const failureMessage = statusQuery.data?.failureMessage ?? result?.failureMessage ?? null
  return { liveStatus, failureMessage, longWait }
}
