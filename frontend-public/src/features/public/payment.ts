import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { useDonationStatusQuery } from './hooks'
import type { PublicDonationResult, PublicDonationStatus, PublicPaymentChannel } from './types'

/** Au-delà de ce délai d'attente de la validation Mobile Money, on explique au payeur qu'il peut fermer. */
const LONG_WAIT_MS = 120_000

/** Numéro saisi : 9 chiffres, ou 12 avec l'indicatif 237. */
export function phoneDigitsOf(phone: string) {
  const digits = phone.replace(/\D/g, '')
  return { digits, valid: digits.length === 9 || (digits.length === 12 && digits.startsWith('237')) }
}

export type OperatorCode = 'ORANGE_MONEY' | 'MTN_MOMO'

/**
 * Opérateur Mobile Money d'après le préfixe (plan de numérotation camerounais) :
 * MTN 67x, 650–654, 680–683 ; Orange 69x, 655–659, 686–689, 640. Nexttel (66x) et Camtel (62x)
 * n'ont pas de Mobile Money accepté ici. Préfixe inconnu (ex. 684, 685) : on laisse choisir.
 */
export function detectOperator(digits: string): OperatorCode | 'UNSUPPORTED' | null {
  const national = digits.length === 12 && digits.startsWith('237') ? digits.slice(3) : digits
  if (national.length !== 9 || !national.startsWith('6')) return null
  const p2 = national.slice(0, 2)
  const p3 = Number(national.slice(0, 3))
  if (p2 === '67' || (p3 >= 650 && p3 <= 654) || (p3 >= 680 && p3 <= 683)) return 'MTN_MOMO'
  if (p2 === '69' || (p3 >= 655 && p3 <= 659) || (p3 >= 686 && p3 <= 689) || p3 === 640) return 'ORANGE_MONEY'
  if (p2 === '66' || p2 === '62') return 'UNSUPPORTED'
  return null
}

/**
 * Choix du moyen de paiement, commun aux deux parcours (don à une collecte, contribution
 * mensuelle) : un seul champ numéro, l'opérateur se déduit du préfixe. Si cet opérateur n'est pas
 * proposé en ce moment, on le dit tout de suite, sans envoyer de demande. « Payer autrement »
 * ouvre les canaux manuels (espèces, virement…) ; le numéro sert alors seulement à joindre la personne.
 */
export function usePaymentChoice(channels: PublicPaymentChannel[] | undefined) {
  const list = channels ?? []
  const onlineChannels = list.filter((c) => c.online)
  const manualChannels = list.filter((c) => !c.online)
  const [phone, setPhone] = useState('')
  const [otherWay, setOtherWay] = useState(false)
  const [manualId, setManualId] = useState<number | null>(null)
  const [pickedOnlineId, setPickedOnlineId] = useState<number | null>(null)

  const { digits, valid } = phoneDigitsOf(phone)
  // Sans canal en ligne actif, seuls les moyens manuels restent.
  const manualMode = otherWay || (channels !== undefined && onlineChannels.length === 0)
  const detected = valid ? detectOperator(digits) : null

  let channel: PublicPaymentChannel | undefined
  let issue: 'unavailable' | 'unsupported' | 'pickOperator' | null = null
  let detectedChannel: PublicPaymentChannel | undefined
  if (manualMode) {
    channel = manualChannels.find((c) => c.id === manualId)
  } else if (valid) {
    if (detected === 'UNSUPPORTED') {
      issue = 'unsupported'
    } else if (detected) {
      detectedChannel = onlineChannels.find((c) => c.code === detected)
      if (detectedChannel) channel = detectedChannel
      else issue = 'unavailable'
    } else if (onlineChannels.length > 1) {
      issue = 'pickOperator'
      channel = onlineChannels.find((c) => c.id === pickedOnlineId)
    } else {
      channel = onlineChannels[0]
    }
  }

  const online = !!channel?.online
  // En ligne : numéro valide obligatoire. Manuel : numéro facultatif, mais valide s'il est saisi.
  const ready = !!channel && (online ? valid : digits.length === 0 || valid)

  return {
    phone,
    setPhone,
    digits,
    valid,
    detected,
    detectedChannel,
    issue,
    channel,
    online,
    ready,
    manualMode,
    canSwitch: onlineChannels.length > 0 && manualChannels.length > 0,
    setOtherWay: (value: boolean) => {
      setOtherWay(value)
      setManualId(null)
    },
    onlineChannels,
    manualChannels,
    manualId,
    setManualId,
    pickedOnlineId,
    setPickedOnlineId,
    /** Numéro normalisé à envoyer (payerPhone en ligne, contact sinon), ou undefined s'il est vide. */
    phoneToSend: valid ? digits : undefined,
  }
}

export type PaymentChoice = ReturnType<typeof usePaymentChoice>

/**
 * Suivi d'un paiement en ligne tant qu'il n'est pas final : statut en direct, motif d'échec, et
 * « longue attente » au-delà de 2 minutes. À la confirmation, les totaux publics (collectes, mois
 * déjà payés) sont rechargés : plus besoin de rafraîchir la page pour voir son don compté.
 */
export function useOnlinePaymentTracking(result: PublicDonationResult | undefined) {
  const queryClient = useQueryClient()
  const tracking =
    result && result.mode === 'ONLINE' && result.status === 'PROCESSING' ? result.trackingToken : null
  // Suivi en ligne : interroge jusqu'au statut final, puis garde la réponse (contenu du reçu).
  const statusQuery = useDonationStatusQuery(result?.mode === 'ONLINE' ? result.trackingToken : null)
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

  useEffect(() => {
    if (liveStatus === 'CONFIRMED') {
      void queryClient.invalidateQueries({ queryKey: ['public-collections'] })
      void queryClient.invalidateQueries({ queryKey: ['public-monthly-contribution'] })
    }
  }, [liveStatus, queryClient])

  const failureMessage = statusQuery.data?.failureMessage ?? result?.failureMessage ?? null
  return {
    liveStatus,
    failureMessage,
    longWait,
    receipt: liveStatus === 'CONFIRMED' ? statusQuery.data : undefined,
  }
}
