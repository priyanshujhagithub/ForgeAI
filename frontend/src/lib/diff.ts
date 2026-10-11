import { structuredPatch } from 'diff'

export interface DiffLine {
  kind: 'context' | 'add' | 'remove'
  text: string
  /** Line number in the old file; null for added lines. */
  oldNumber: number | null
  /** Line number in the new file; null for removed lines. */
  newNumber: number | null
}

export interface DiffHunk {
  header: string
  lines: DiffLine[]
}

export interface FileDiff {
  hunks: DiffHunk[]
  added: number
  removed: number
}

const CONTEXT_LINES = 3

/** A unified diff between two versions of a file, with line numbers for both sides. */
export function diffFile(before: string, after: string): FileDiff {
  const patch = structuredPatch('a', 'b', before, after, '', '', { context: CONTEXT_LINES })

  let added = 0
  let removed = 0

  const hunks = patch.hunks.map((hunk) => {
    let oldNumber = hunk.oldStart
    let newNumber = hunk.newStart
    const lines: DiffLine[] = []

    for (const raw of hunk.lines) {
      const marker = raw[0]
      const text = raw.slice(1)

      if (marker === '+') {
        lines.push({ kind: 'add', text, oldNumber: null, newNumber: newNumber++ })
        added++
      } else if (marker === '-') {
        lines.push({ kind: 'remove', text, oldNumber: oldNumber++, newNumber: null })
        removed++
      } else if (marker === ' ') {
        lines.push({ kind: 'context', text, oldNumber: oldNumber++, newNumber: newNumber++ })
      }
      // "\ No newline at end of file" markers are not content; skip them.
    }

    return {
      header: `@@ -${hunk.oldStart},${hunk.oldLines} +${hunk.newStart},${hunk.newLines} @@`,
      lines,
    }
  })

  return { hunks, added, removed }
}
