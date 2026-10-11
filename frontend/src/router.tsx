import { createBrowserRouter } from 'react-router'
import type { RouteObject } from 'react-router'
import { AppShell } from './components/AppShell'
import { RequireAuth, RouteError } from './components/RouteGuards'
import { AuthPage } from './pages/Auth'
import { Landing } from './pages/Landing'
import { NewProject } from './pages/NewProject'
import { NotFound } from './pages/NotFound'
import { ProjectPage } from './pages/ProjectPage'
import { Projects } from './pages/Projects'
import { RunPage } from './pages/RunPage'
import { Settings } from './pages/Settings'

export const routes: RouteObject[] = [
  {
    errorElement: <RouteError />,
    children: [
      { path: '/', element: <Landing /> },
      { path: '/login', element: <AuthPage mode="login" /> },
      { path: '/register', element: <AuthPage mode="register" /> },
      {
        path: '/app',
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, element: <Projects /> },
              { path: 'projects/new', element: <NewProject /> },
              { path: 'projects/:projectId', element: <ProjectPage /> },
              { path: 'runs/:runId', element: <RunPage /> },
              { path: 'settings', element: <Settings /> },
              { path: '*', element: <NotFound /> },
            ],
          },
        ],
      },
      { path: '*', element: <NotFound /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
