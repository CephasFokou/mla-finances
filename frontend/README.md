# mla-finance-frontend

Application front-office/back-office du système de gestion financière MLA (ICC Yaoundé). React 19 + TypeScript, Vite, TanStack Query, Zustand, React Hook Form + Zod, react-i18next, SCSS Modules.

## Démarrer en local

```bash
npm install
npm run dev
```

## Scripts

- `npm run dev` — serveur de développement Vite.
- `npm run build` — vérification des types (`tsc -b`) puis build de production.
- `npm run lint` — ESLint.
- `npm run format` / `npm run format:check` — Prettier.
- `npm run preview` — sert le build de production en local.

## Structure

```
src/
  app/            routage (react-router-dom)
  components/     composants partagés (ex. AppLayout : coquille back-office)
  design-system/  tokens SCSS (couleurs, typo, espacements) + styles globaux
  i18n/           configuration react-i18next + locales (fr uniquement pour l'instant)
  lib/            clients partagés (TanStack Query, etc.)
  pages/          un dossier par écran/module (login, dashboard, puis contributions, projets…)
  store/          stores Zustand, un par domaine
```

Le design system (palette navy/or, Bodoni Moda + Familjen Grotesk) reprend directement l'identité validée dans les maquettes HTML/CSS du back-office. Chaque nouveau module (Contributions, Projets, Retraits, Rapports, Administration, Médias) ajoute son dossier sous `src/pages` et ses routes dans `src/app/router.tsx`, en consommant les tokens plutôt que des valeurs codées en dur.

## Déploiement

Netlify (voir `netlify.toml`) : build `npm run build`, publication du dossier `dist/`, redirection SPA vers `index.html`.
