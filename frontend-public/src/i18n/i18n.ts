import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import fr from './locales/fr/common.json'

// Même convention « zéro texte en dur » que le back-office (voir frontend/src/i18n/i18n.ts), mais
// sans i18next-browser-languagedetector : une seule langue pour ce site public, la détection de
// langue navigateur n'apporterait rien et alourdirait inutilement une page destinée au grand
// public.
void i18n.use(initReactI18next).init({
  resources: {
    fr: { common: fr },
  },
  ns: ['common'],
  defaultNS: 'common',
  lng: 'fr',
  fallbackLng: 'fr',
  supportedLngs: ['fr'],
  interpolation: {
    escapeValue: false,
  },
})

export default i18n
