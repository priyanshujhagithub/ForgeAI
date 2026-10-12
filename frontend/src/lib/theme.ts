import { useSyncExternalStore } from 'react'
import { flushSync } from 'react-dom'
import { prefersReducedMotion, useMediaQuery } from './motion'

// Light, dark, or whatever the operating system says.
//
// The choice is stored as a `data-theme` attribute on <html>, which tokens.css
// turns into a `color-scheme`. "System" removes the attribute. A small script
// in index.html applies the stored choice before the first paint, so a reload
// never flashes the wrong theme.

export type ThemePreference = 'system' | 'light' | 'dark'
export type Theme = 'light' | 'dark'

const KEY = 'forgeai.theme'
const DARK = '(prefers-color-scheme: dark)'

const listeners = new Set<() => void>()

function readPreference(): ThemePreference {
  try {
    const stored = localStorage.getItem(KEY)
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    return 'system'
  }
}

function systemTheme(): Theme {
  return typeof window.matchMedia === 'function' && window.matchMedia(DARK).matches
    ? 'dark'
    : 'light'
}

function resolve(preference: ThemePreference): Theme {
  return preference === 'system' ? systemTheme() : preference
}

function apply(preference: ThemePreference) {
  const root = document.documentElement
  if (preference === 'system') delete root.dataset.theme
  else root.dataset.theme = preference

  try {
    if (preference === 'system') localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, preference)
  } catch {
    // Storage can be unavailable (private windows); the choice then lasts until reload.
  }
  listeners.forEach((listener) => listener())
}

/**
 * Changes the theme. Given the point that was clicked, the new theme spreads
 * out from it in a circle (the styles are in transitions.css); without one,
 * or without the View Transitions API, it changes at once.
 */
export function setTheme(preference: ThemePreference, origin?: { x: number; y: number }) {
  const changesColours = resolve(preference) !== resolve(readPreference())
  const animate =
    changesColours &&
    origin !== undefined &&
    typeof document.startViewTransition === 'function' &&
    !prefersReducedMotion()

  if (!animate) {
    apply(preference)
    return
  }

  const root = document.documentElement
  // Far enough to reach the corner of the window furthest from the click.
  const radius = Math.hypot(
    Math.max(origin.x, window.innerWidth - origin.x),
    Math.max(origin.y, window.innerHeight - origin.y),
  )
  root.style.setProperty('--vt-x', `${origin.x}px`)
  root.style.setProperty('--vt-y', `${origin.y}px`)
  root.style.setProperty('--vt-radius', `${radius}px`)
  root.dataset.vt = 'theme'

  const transition = document.startViewTransition(() => flushSync(() => apply(preference)))
  void transition.finished.finally(() => {
    delete root.dataset.vt
    for (const name of ['--vt-x', '--vt-y', '--vt-radius']) root.style.removeProperty(name)
  })
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function useTheme(): { preference: ThemePreference; theme: Theme } {
  const preference = useSyncExternalStore(subscribe, readPreference)
  // Re-render when the operating system switches, which matters only for "system".
  const systemIsDark = useMediaQuery(DARK)
  const theme = preference === 'system' ? (systemIsDark ? 'dark' : 'light') : preference
  return { preference, theme }
}
