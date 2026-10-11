// Where the login token lives between page loads.
//
// localStorage keeps the user signed in across tabs and reloads. The trade-off
// is that any script running on this origin could read it, so the app ships no
// third-party scripts and renders no user-supplied HTML. A production system
// would use an HttpOnly cookie instead.

const KEY = 'forgeai.token'

export interface Session {
  token: string
  email: string
  expiresAt: number
}

type Listener = (session: Session | null) => void
const listeners = new Set<Listener>()

/** Reads the claims a JWT carries. This is for display only: the API verifies the signature. */
function decode(token: string): Session | null {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    const claims = JSON.parse(json) as { email?: string; exp?: number }
    if (!claims.email || !claims.exp) return null
    return { token, email: claims.email, expiresAt: claims.exp * 1000 }
  } catch {
    return null
  }
}

export function readSession(): Session | null {
  const token = localStorage.getItem(KEY)
  if (!token) return null

  const session = decode(token)
  if (!session || session.expiresAt <= Date.now()) {
    localStorage.removeItem(KEY)
    return null
  }
  return session
}

export function writeSession(token: string): Session | null {
  localStorage.setItem(KEY, token)
  const session = readSession()
  listeners.forEach((listener) => listener(session))
  return session
}

export function clearSession(): void {
  localStorage.removeItem(KEY)
  listeners.forEach((listener) => listener(null))
}

export function onSessionChange(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

// Signing in or out in one tab applies to the others too.
window.addEventListener('storage', (event) => {
  if (event.key !== KEY) return
  const session = readSession()
  listeners.forEach((listener) => listener(session))
})
