import { createBrowserRouter } from 'react-router-dom'

import { AppLayout } from '../components/layout/AppLayout'
import { LoginPage } from '../pages/login/LoginPage'
import { ActivateAccountPage } from '../pages/auth/ActivateAccountPage'
import { ForgotPasswordPage } from '../pages/auth/ForgotPasswordPage'
import { ResetPasswordPage } from '../pages/auth/ResetPasswordPage'
import { SessionExpiredPage } from '../pages/auth/SessionExpiredPage'
import { UnauthorizedPage } from '../pages/auth/UnauthorizedPage'
import { DashboardPage } from '../pages/dashboard/DashboardPage'
import { ContributionsListPage } from '../pages/contributions/ContributionsListPage'
import { ContributionFormPage } from '../pages/contributions/ContributionFormPage'
import { ContributionDetailPage } from '../pages/contributions/ContributionDetailPage'
import { ProjectsListPage } from '../pages/projects/ProjectsListPage'
import { ProjectFormPage } from '../pages/projects/ProjectFormPage'
import { ProjectDetailPage } from '../pages/projects/ProjectDetailPage'
import { WithdrawalsListPage } from '../pages/withdrawals/WithdrawalsListPage'
import { WithdrawalFormPage } from '../pages/withdrawals/WithdrawalFormPage'
import { WithdrawalDetailPage } from '../pages/withdrawals/WithdrawalDetailPage'
import { ReportsPage } from '../pages/reports/ReportsPage'
import { AdministrationPage } from '../pages/administration/AdministrationPage'
import { UserInvitePage } from '../pages/administration/UserInvitePage'
import { MediaPage } from '../pages/media/MediaPage'
import { NewBannerPage } from '../pages/media/NewBannerPage'
import { BannerOrderPage } from '../pages/media/BannerOrderPage'
import { NewVisualPage } from '../pages/media/NewVisualPage'
import { RequireAuth, RequirePermission } from './guards'

// Squelette de routage : un module = un dossier sous src/pages, monté ici au fil de
// l'implémentation (Contributions, Projets, Retraits, Rapports, Administration, Médias,
// Authentification). RG-18 : tout l'arbre back-office est protégé par RequireAuth (voir
// app/guards.tsx) ; Administration est en outre protégée par RequirePermission côté client (la
// protection réelle reste @PreAuthorize côté backend).
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/activate', element: <ActivateAccountPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '/session-expired', element: <SessionExpiredPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/',
        element: <AppLayout />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'contributions', element: <ContributionsListPage /> },
          { path: 'contributions/new', element: <ContributionFormPage /> },
          { path: 'contributions/:id', element: <ContributionDetailPage /> },
          { path: 'projects', element: <ProjectsListPage /> },
          { path: 'projects/new', element: <ProjectFormPage /> },
          { path: 'projects/:id', element: <ProjectDetailPage /> },
          { path: 'withdrawals', element: <WithdrawalsListPage /> },
          { path: 'withdrawals/new', element: <WithdrawalFormPage /> },
          { path: 'withdrawals/:id', element: <WithdrawalDetailPage /> },
          { path: 'reports', element: <ReportsPage /> },
          {
            path: 'administration',
            element: (
              <RequirePermission permission="MANAGE_USERS">
                <AdministrationPage />
              </RequirePermission>
            ),
          },
          {
            path: 'administration/users/new',
            element: (
              <RequirePermission permission="MANAGE_USERS">
                <UserInvitePage />
              </RequirePermission>
            ),
          },
          { path: 'media', element: <MediaPage /> },
          { path: 'media/banners/new', element: <NewBannerPage /> },
          { path: 'media/banners/order', element: <BannerOrderPage /> },
          { path: 'media/visuals/new', element: <NewVisualPage /> },
          { path: 'unauthorized', element: <UnauthorizedPage /> },
        ],
      },
    ],
  },
])
