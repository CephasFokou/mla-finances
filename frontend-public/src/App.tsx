import { PublicDonationPage } from './pages/PublicDonationPage'
import { ReminderActionPage } from './pages/ReminderActionPage'

// Site à une seule page, sans routeur (voir claude/module-front-office-public-spec.md). Seule exception : la page de
// réponse des emails de relance (/relance/{jeton}). Le lien « Contribuer maintenant » (/c/{jeton}) est traité par la
// page principale. Netlify renvoie toutes les adresses vers index.html.
const RELANCE = /^\/relance\/([A-Za-z0-9_-]{16,64})\/?$/

export default function App() {
  const match = RELANCE.exec(window.location.pathname)
  if (match) {
    const action = new URLSearchParams(window.location.search).get('a')
    return (
      <ReminderActionPage token={match[1]!} initial={action === 'ack' || action === 'done' ? action : null} />
    )
  }
  return <PublicDonationPage />
}
