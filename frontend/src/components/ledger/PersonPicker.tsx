import { useState } from 'react'
import type { UseFormRegisterReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { useDonorSearchQuery } from '../../features/reference-data/hooks'
import ui from '../ui/ui.module.scss'

interface PersonPickerProps {
  label: string
  registration: UseFormRegisterReturn
  disabled?: boolean
  emptyLabel: string
}

/** Recherche + sélection d'un membre ou d'un tiers (nom usuel du journal, ex. « Maman Jeanne »). */
export function PersonPicker({ label, registration, disabled, emptyLabel }: PersonPickerProps) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const { data: people } = useDonorSearchQuery(query)

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1.4fr)',
        gap: 'var(--space-2)',
      }}
    >
      <input
        type="search"
        value={query}
        disabled={disabled}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={t('ledger.personSearch')}
        aria-label={t('ledger.personSearch')}
      />
      <select disabled={disabled} aria-label={label} {...registration}>
        <option value="">{emptyLabel}</option>
        {people?.map((p) => (
          <option key={p.id} value={p.id}>
            {p.displayName || p.fullName}
            {p.memberRole ? ` · ${t(`memberRole.${p.memberRole}`)}` : ` · ${t('memberRole.THIRD_PARTY')}`}
          </option>
        ))}
      </select>
      <span className={ui.hint} style={{ gridColumn: '1 / -1' }}>
        {t('ledger.personHint')}
      </span>
    </div>
  )
}
