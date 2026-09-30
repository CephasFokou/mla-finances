import { PublicDonationPage } from './pages/PublicDonationPage'

// Site à une seule page : pas de routeur ici (voir claude/module-front-office-public-spec.md pour
// le contexte de la séparation d'avec le back-office, qui lui a son propre react-router-dom).
export default function App() {
  return <PublicDonationPage />
}
