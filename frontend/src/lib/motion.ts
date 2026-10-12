import { useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore } from 'react'
import type { CSSProperties, RefObject } from 'react'

// The pieces of motion that CSS cannot do alone. Everything here degrades to
// "no animation": if the browser lacks a feature, or the user has asked the
// operating system for less motion, content simply appears.

const REDUCED_MOTION = '(prefers-reduced-motion: reduce)'

/**
 * An item's position in its list, as the `--i` the staggered animations in
 * base.css read: `<li className="enter" style={nth(index)}>`.
 */
export function nth(index: number): CSSProperties {
  return { '--i': index } as CSSProperties
}

function matches(query: string): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(query).matches
}

export function prefersReducedMotion(): boolean {
  return matches(REDUCED_MOTION)
}

/** Whether a media query matches, kept up to date when it changes. */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (notify) => {
      if (typeof window.matchMedia !== 'function') return () => undefined
      const list = window.matchMedia(query)
      list.addEventListener('change', notify)
      return () => list.removeEventListener('change', notify)
    },
    () => matches(query),
  )
}

export function usePrefersReducedMotion(): boolean {
  return useMediaQuery(REDUCED_MOTION)
}

/**
 * Reveals every `[data-reveal]` element inside the returned ref the first time
 * it scrolls into view. The styles are in base.css.
 *
 * An element that is already above the viewport (the page was opened at an
 * anchor further down) is shown at once: animating something the reader has
 * already scrolled past would only be noise when they scroll back up.
 */
export function useRevealOnScroll<T extends HTMLElement>(): RefObject<T | null> {
  const container = useRef<T>(null)

  useLayoutEffect(() => {
    const root = container.current
    if (!root || prefersReducedMotion() || typeof IntersectionObserver === 'undefined') return

    const items = Array.from(root.querySelectorAll<HTMLElement>('[data-reveal]'))
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          const passed = entry.boundingClientRect.bottom < 0
          if (!entry.isIntersecting && !passed) continue
          const item = entry.target as HTMLElement
          if (passed) delete item.dataset.revealState
          else item.dataset.revealState = 'shown'
          observer.unobserve(item)
        }
      },
      // Wait until an element is a little way into the viewport, not at its very edge.
      { rootMargin: '0px 0px -10% 0px', threshold: 0.1 },
    )

    for (const item of items) {
      item.dataset.revealState = 'pending'
      observer.observe(item)
    }
    return () => {
      observer.disconnect()
      for (const item of items) delete item.dataset.revealState
    }
  }, [])

  return container
}

/** The id of the section currently crossing the middle of the viewport, if any. */
export function useActiveSection(ids: readonly string[]): string | null {
  const [active, setActive] = useState<string | null>(null)

  useEffect(() => {
    if (typeof IntersectionObserver === 'undefined') return

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          const { id } = entry.target
          if (entry.isIntersecting) setActive(id)
          else setActive((current) => (current === id ? null : current))
        }
      },
      // A thin band across the middle of the viewport: one section at a time.
      { rootMargin: '-45% 0px -50% 0px' },
    )

    for (const id of ids) {
      const section = document.getElementById(id)
      if (section) observer.observe(section)
    }
    return () => observer.disconnect()
  }, [ids])

  return active
}
