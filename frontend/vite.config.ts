import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
// Port fixé et strict : FRONTEND_ORIGIN (backend/.env) est une origine CORS exacte — si 5173 est
// déjà pris, mieux vaut échouer tout de suite que démarrer sur 5174 et voir toutes les requêtes
// rejetées par le CORS (cas déjà rencontré lors des premiers tests).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
})
