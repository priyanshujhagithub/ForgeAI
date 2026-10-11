import { useRef } from 'react'
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
 */
export function Tabs({ tabs, selected, onSelect, label, children }: TabsProps) {
  const list = useRef<HTMLDivElement>(null)

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
              {tab.count !== undefined && <span className={styles.count}>{tab.count}</span>}
            </button>
          )
        })}
      </div>
      <div
        role="tabpanel"
        id={`panel-${selected}`}
        aria-labelledby={`tab-${selected}`}
        tabIndex={0}
        className={styles.panel}
      >
        {children}
      </div>
    </div>
  )
}
