import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeToggle } from '../components/ThemeToggle'
import { setTheme, useTheme } from './theme'

const root = document.documentElement

/** Stands in for the operating system's light or dark setting, which jsdom does not have. */
function systemPrefers(scheme: 'light' | 'dark') {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query.includes('dark') ? scheme === 'dark' : false,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }))
}

function Probe() {
  const { preference, theme } = useTheme()
  return <p>{`${preference} / ${theme}`}</p>
}

afterEach(() => {
  act(() => setTheme('system'))
  vi.unstubAllGlobals()
})

describe('theme', () => {
  it('follows the operating system until a choice is made', () => {
    systemPrefers('dark')
    render(<Probe />)

    expect(screen.getByText('system / dark')).toBeInTheDocument()
    expect(root.dataset.theme).toBeUndefined()
  })

  it('applies and remembers an explicit choice', () => {
    systemPrefers('dark')
    render(<Probe />)

    act(() => setTheme('light'))

    expect(screen.getByText('light / light')).toBeInTheDocument()
    expect(root.dataset.theme).toBe('light')
    expect(localStorage.getItem('forgeai.theme')).toBe('light')
  })

  it('forgets the choice when handed back to the system', () => {
    systemPrefers('light')
    render(<Probe />)
    act(() => setTheme('dark'))

    act(() => setTheme('system'))

    expect(screen.getByText('system / light')).toBeInTheDocument()
    expect(root.dataset.theme).toBeUndefined()
    expect(localStorage.getItem('forgeai.theme')).toBeNull()
  })

  it('changes at once, with no view transition, when the browser has none', () => {
    systemPrefers('light')
    // jsdom has no document.startViewTransition, like an older browser.
    expect('startViewTransition' in document).toBe(false)

    setTheme('dark', { x: 10, y: 10 })

    expect(root.dataset.theme).toBe('dark')
    expect(root.dataset.vt).toBeUndefined()
  })
})

describe('ThemeToggle', () => {
  it('names the theme it will switch to, and switches', async () => {
    systemPrefers('light')
    const user = userEvent.setup()
    render(<ThemeToggle />)

    await user.click(screen.getByRole('button', { name: 'Switch to dark theme' }))

    expect(root.dataset.theme).toBe('dark')
    expect(screen.getByRole('button', { name: 'Switch to light theme' })).toBeInTheDocument()
  })
})
