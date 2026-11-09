import { useEffect, useState } from 'react'

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/**
 * Fait défiler un nombre de 0 jusqu'à sa valeur (ease-out), une fois à chaque changement de
 * valeur : le seul mouvement « non demandé » du back-office, sur la Synthèse. Valeur finale
 * immédiate si l'appareil demande moins d'animations.
 */
export function useCountUp(target: number | undefined, durationMs = 1200): number {
  const reduce = prefersReducedMotion()
  const [value, setValue] = useState(0)

  useEffect(() => {
    if (target === undefined || reduce) return
    let frame = 0
    const start = performance.now()
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs)
      setValue(target * (1 - Math.pow(1 - t, 3)))
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, durationMs, reduce])

  return reduce ? (target ?? 0) : value
}
