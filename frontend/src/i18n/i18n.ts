import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import fr from './locales/fr/common.json'

// Zéro texte en dur dans les composants (Document Technique §4) : toute chaîne affichée passe
// par react-i18next, même si le français est aujourd'hui la seule langue supportée. Ajouter une
// langue = ajouter un fichier locales/<lang>/common.json + l'enregistrer ci-dessous.
void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      fr: { common: fr },
    },
    ns: ['common'],
    defaultNS: 'common',
    fallbackLng: 'fr',
    supportedLngs: ['fr'],
    interpolation: {
      escapeValue: false,
    },
  })

export default i18n
