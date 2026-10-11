import { Navigate, Outlet, useLocation, useRouteError } from 'react-router'
import { useAuth } from '../lib/auth'
import { ErrorNotice } from './Notice'

/** Everything under /app needs a session. Without one, go to sign-in and come back after. */
export function RequireAuth() {
  const { session } = useAuth()
  const location = useLocation()

  if (!session) {
    const from = location.pathname + location.search
    return <Navigate to="/login" replace state={{ from }} />
  }
  return <Outlet />
}

/** Last line of defence: a rendering error shows a message instead of a blank page. */
export function RouteError() {
  const error = useRouteError()
  return (
    <div className="container" style={{ paddingBlock: 'var(--space-8)' }}>
      <ErrorNotice error={error} title="This page could not be shown" />
    </div>
  )
}
