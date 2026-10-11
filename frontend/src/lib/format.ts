/**
 * The API sends timestamps in UTC without a zone suffix
 * ("2026-10-11T16:24:05.123"). Without the "Z" a browser would read them as
 * local time and every "5 minutes ago" would be off by the user's UTC offset.
 */
export function parseServerDate(value: string): Date {
  return new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`)
}

const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['day', 86_400],
  ['hour', 3_600],
  ['minute', 60],
]

export function timeAgo(value: string, now: Date = new Date()): string {
  const seconds = Math.round((parseServerDate(value).getTime() - now.getTime()) / 1000)
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) return relative.format(Math.round(seconds / size), unit)
  }
  return 'just now'
}

export function formatDateTime(value: string): string {
  return parseServerDate(value).toLocaleString('en', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** "4 min 05 s", "48 s". */
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.round(totalSeconds))
  if (seconds < 60) return `${seconds} s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes} min ${String(seconds % 60).padStart(2, '0')} s`
}

export function secondsBetween(start: string, end: string | null, now: Date = new Date()): number {
  const finish = end ? parseServerDate(end) : now
  return (finish.getTime() - parseServerDate(start).getTime()) / 1000
}

/** Sandbox costs are fractions of a cent, so show enough digits to be meaningful. */
export function formatCost(usd: number): string {
  return `$${usd.toFixed(usd < 0.01 ? 4 : 2)}`
}

export function shortSha(sha: string): string {
  return sha.slice(0, 7)
}
