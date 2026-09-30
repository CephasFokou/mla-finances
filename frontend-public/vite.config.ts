import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Site public de don, volontairement détaché du bundle back-office (voir ../frontend) : projet
// Vite indépendant, avec son propre build et son propre déploiement, pour ne jamais faire
// transiter le code de l'application authentifiée sur une page ouverte à tout le monde (voir
// claude/module-front-office-public-spec.md). Port fixe pour pouvoir lancer les deux apps en
// parallèle en dev sans collision avec le 5173 par défaut du back-office.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5180,
  },
})
