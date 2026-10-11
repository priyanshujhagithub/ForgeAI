import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { AuthContext } from '../lib/auth'
import { clearSession, onSessionChange, readSession, writeSession } from '../lib/session'
import type { Session } from '../lib/session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => readSession())
  const queryClient = useQueryClient()

  // The session can end outside React: the API client clears it on a 401, and
  // another tab may sign out. Either way this component hears about it.
  useEffect(() => onSessionChange(setSession), [])

  useEffect(() => {
    // Cached data belongs to whoever was signed in; never show it to the next user.
    if (!session) queryClient.clear()
  }, [session, queryClient])

  const signIn = useCallback(async (email: string, password: string) => {
    const { accessToken } = await api.login(email, password)
    writeSession(accessToken)
  }, [])

  const signUp = useCallback(
    async (email: string, password: string) => {
      await api.register(email, password)
      await signIn(email, password)
    },
    [signIn],
  )

  const value = useMemo(
    () => ({ session, signIn, signUp, signOut: clearSession }),
    [session, signIn, signUp],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
