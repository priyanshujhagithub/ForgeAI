import { useLayoutEffect, useRef } from 'react'
import type { KeyboardEvent, ReactNode } from 'react'
import styles from './Tabs.module.css'

export interface Tab {
  id: string
  label: string
  /** A small count shown next to the label, such as the number of files changed. */
  count?: number
}

interface TabsProps {
  tabs: Tab[]
  selected: string
  onSelect: (id: string) => void
  label: string
  children: ReactNode
}

/**
 * Tabs that follow the WAI-ARIA pattern: one tab stop for the whole list,
 * arrow keys to move between tabs, Home and End to jump to the ends.
 *
 * One line marks the selected tab and slides to the next one you pick.
 */
export function Tabs({ tabs, selected, onSelect, label, children }: TabsProps) {
  const list = useRef<HTMLDivElement>(null)

  // Labels and counts change a tab's width, so the line is measured again
  // whenever any of them does.
  const shape = tabs.map((tab) => `${tab.id}:${tab.label}:${tab.count ?? ''}`).join('|')

  useLayoutEffect(() => {
    const element = list.current
    const tab = element?.querySelector<HTMLElement>('[aria-selected="true"]')
    if (!element || !tab) return

    // Written straight to the element: measuring must not cause a re-render.
    const place = () => {
      element.style.setProperty('--line-x', `${tab.offsetLeft}px`)
      element.style.setProperty('--line-width', String(tab.offsetWidth))
    }
    place()

    // On a narrow screen the list scrolls sideways; keep the selection in view.
    if (element.dataset.placed) tab.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
    // The line appears in place the first time, and only slides after that.
    const frame = requestAnimationFrame(() => {
      element.dataset.placed = 'true'
    })

    if (typeof ResizeObserver === 'undefined') return () => cancelAnimationFrame(frame)
    const observer = new ResizeObserver(place)
    observer.observe(element)
    observer.observe(tab)
    return () => {
      cancelAnimationFrame(frame)
      observer.disconnect()
    }
  }, [selected, shape])

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const current = tabs.findIndex((tab) => tab.id === selected)
    const last = tabs.length - 1
    const next = {
      ArrowRight: current === last ? 0 : current + 1,
      ArrowLeft: current === 0 ? last : current - 1,
      Home: 0,
      End: last,
    }[event.key]

    if (next === undefined) return
    event.preventDefault()
    onSelect(tabs[next].id)
    list.current?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus()
  }

  return (
    <div>
      <div
        ref={list}
        role="tablist"
        aria-label={label}
        className={styles.list}
        onKeyDown={onKeyDown}
      >
        {tabs.map((tab) => {
          const active = tab.id === selected
          return (
            <button
              key={tab.id}
              role="tab"
              type="button"
              id={`tab-${tab.id}`}
              aria-selected={active}
              aria-controls={`panel-${tab.id}`}
              tabIndex={active ? 0 : -1}
              className={`${styles.tab} ${active ? styles.active : ''}`}
              onClick={() => onSelect(tab.id)}
            >
              {tab.label}
              {tab.count !== undefined && (
                // Keyed by its value, so the badge pops when the number changes.
                <span key={tab.count} className={styles.count}>
                  {tab.count}
                </span>
              )}
            </button>
          )
        })}
        <span className={styles.line} aria-hidden="true" />
      </div>
      <div
        role="tabpanel"
        id={`panel-${selected}`}
        aria-labelledby={`tab-${selected}`}
        tabIndex={0}
        className={styles.panel}
      >
        {/* Keyed by tab, so each panel arrives instead of swapping in place. */}
        <div key={selected} className="enter">
          {children}
        </div>
      </div>
    </div>
  )
}
