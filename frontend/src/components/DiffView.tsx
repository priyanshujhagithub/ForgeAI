import { useMemo } from 'react'
import { diffFile } from '../lib/diff'
import { useOriginalFile } from '../lib/queries'
import type { CodeChange } from '../lib/types'
import styles from './DiffView.module.css'

interface DiffViewProps {
  projectId: number
  change: CodeChange
  /** The commit the run started from. The diff is always against this, not today's branch. */
  baseSha: string | null
}

const ACTION_LABEL = { modify: 'Modified', create: 'New file', delete: 'Deleted' } as const

export function DiffView({ projectId, change, baseSha }: DiffViewProps) {
  // A new file has nothing to compare against; skip the request.
  const needsOriginal = change.action !== 'create'
  const original = useOriginalFile(projectId, change.path, baseSha, needsOriginal)

  const before = needsOriginal ? original.data?.content : ''
  const after = change.action === 'delete' ? '' : change.content

  const diff = useMemo(
    () => (before === undefined ? null : diffFile(before, after)),
    [before, after],
  )

  return (
    <article className={styles.file}>
      <header className={styles.header}>
        <div className={styles.heading}>
          <span className={`${styles.action} ${styles[change.action]}`}>
            {ACTION_LABEL[change.action]}
          </span>
          <h3 className={styles.path}>{change.path}</h3>
        </div>
        {diff && (
          <p className={styles.stats} aria-label={`${diff.added} lines added, ${diff.removed} removed`}>
            <span className={styles.added}>+{diff.added}</span>
            <span className={styles.removed}>−{diff.removed}</span>
          </p>
        )}
      </header>

      {change.explanation && <p className={styles.explanation}>{change.explanation}</p>}

      {original.isLoading && <p className={styles.message}>Loading the original file…</p>}

      {original.isError && (
        <p className={styles.message}>
          The original file could not be loaded, so the new content is shown without a comparison.
        </p>
      )}

      {diff && diff.hunks.length === 0 && (
        <p className={styles.message}>No textual difference from the original.</p>
      )}

      {diff && diff.hunks.length > 0 && <DiffTable diff={diff} />}

      {original.isError && <pre className={styles.raw}>{after}</pre>}
    </article>
  )
}

function DiffTable({ diff }: { diff: ReturnType<typeof diffFile> }) {
  return (
    // Long lines scroll inside the table; the page itself never scrolls sideways.
    <div className={styles.scroll} tabIndex={0} role="group" aria-label="File changes">
      <table className={styles.table}>
        <tbody>
          {diff.hunks.map((hunk) => (
            <Hunk key={hunk.header} hunk={hunk} />
          ))}
        </tbody>
      </table>
    </div>
  )
}

function Hunk({ hunk }: { hunk: ReturnType<typeof diffFile>['hunks'][number] }) {
  return (
    <>
      <tr className={styles.hunk}>
        <td colSpan={4}>{hunk.header}</td>
      </tr>
      {hunk.lines.map((line, index) => (
        <tr key={index} className={styles[line.kind]}>
          <td className={styles.number}>{line.oldNumber ?? ''}</td>
          <td className={styles.number}>{line.newNumber ?? ''}</td>
          <td className={styles.marker} aria-hidden="true">
            {line.kind === 'add' ? '+' : line.kind === 'remove' ? '−' : ''}
          </td>
          <td className={styles.code}>
            <span className="visually-hidden">
              {line.kind === 'add' ? 'Added: ' : line.kind === 'remove' ? 'Removed: ' : ''}
            </span>
            {line.text || ' '}
          </td>
        </tr>
      ))}
    </>
  )
}
