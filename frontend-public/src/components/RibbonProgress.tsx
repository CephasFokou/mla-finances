import { useEffect, useRef, useState } from 'react'

import styles from './RibbonProgress.module.scss'

const PATH = 'M6 26 C 110 30, 230 18, 394 6'

/**
 * « Le ruban qui se remplit » : la courbe d'un ruban du logo se dessine quand elle entre à
 * l'écran. Sans objectif de collecte connu, le ruban se dessine en entier : il souligne le
 * montant reçu, il ne prétend pas mesurer un pourcentage.
 */
export function RibbonProgress({ color, empty = false }: { color: string; empty?: boolean }) {
  const ref = useRef<SVGSVGElement>(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const node = ref.current
    if (!node || typeof IntersectionObserver === 'undefined') {
      setVisible(true)
      return
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setVisible(true)
          observer.disconnect()
        }
      },
      { threshold: 0.6 },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  return (
    <svg ref={ref} viewBox="0 0 400 34" className={styles.ribbon} aria-hidden="true">
      <path d={PATH} className={styles.track} />
      {!empty && (
        <path
          d={PATH}
          className={`${styles.fill} ${visible ? styles.drawn : ''}`}
          style={{ stroke: color }}
        />
      )}
    </svg>
  )
}
