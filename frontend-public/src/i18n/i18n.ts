import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import en from './locales/en/common.json'
import fr from './locales/fr/common.json'

// Même convention « zéro texte en dur » que le back-office (voir frontend/src/i18n/i18n.ts). Deux
// langues, français par défaut : l'anglais est choisi si le visiteur l'a demandé (bouton FR / EN,
// mémorisé sur cet appareil) ou si son navigateur est en anglais. Pas de bibliothèque de détection :
// ces trois lignes suffisent et gardent la page légère.
export const LANGUAGES = ['fr', 'en'] as const
export type Language = (typeof LANGUAGES)[number]
const STORAGE_KEY = 'mla-public-lang'

function initialLanguage(): Language {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    if (saved === 'fr' || saved === 'en') return saved
  } catch {
    // stockage indisponible (navigation privée, aperçu) : on retombe sur le navigateur
  }
  return navigator.language?.toLowerCase().startsWith('en') ? 'en' : 'fr'
}

export function setLanguage(language: Language) {
  try {
    window.localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // préférence non mémorisée : la langue change quand même pour cette visite
  }
  void i18n.changeLanguage(language)
}

i18n.on('languageChanged', (language) => {
  document.documentElement.lang = language
})

void i18n.use(initReactI18next).init({
  resources: {
    fr: { common: fr },
    en: { common: en },
  },
  ns: ['common'],
  defaultNS: 'common',
  lng: initialLanguage(),
  fallbackLng: 'fr',
  supportedLngs: [...LANGUAGES],
  interpolation: {
    escapeValue: false,
  },
})

export default i18n
