/**
 * The backend serialises java.time.LocalDateTime, which has no timezone and up
 * to 9 fractional-second digits (e.g. "2026-07-30T14:59:27.743110904").
 * Date.parse handles at most milliseconds reliably, so trim to 3 digits before
 * parsing and treat the result as local wall-clock time.
 */
function parseLocalDateTime(value: string): Date | null {
  const trimmed = value.replace(/(\.\d{3})\d+$/, '$1')
  const date = new Date(trimmed)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = parseLocalDateTime(value)
  if (!date) return value
  return date.toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

export function formatRelative(value: string | null | undefined): string | null {
  if (!value) return null
  const date = parseLocalDateTime(value)
  if (!date) return null

  const seconds = Math.round((date.getTime() - Date.now()) / 1000)
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['second', 60],
    ['minute', 60],
    ['hour', 24],
    ['day', 30],
    ['month', 12],
    ['year', Number.POSITIVE_INFINITY],
  ]

  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  let value_ = seconds
  for (const [unit, step] of units) {
    if (Math.abs(value_) < step) return formatter.format(Math.round(value_), unit)
    value_ /= step
  }
  return null
}

/** Maps the backend's free-text severity strings onto badge colours. */
export function severityTone(severity: string): 'danger' | 'warn' | 'info' | 'ok' {
  const value = severity.toLowerCase()
  if (value.includes('high') || value.includes('critical')) return 'danger'
  if (value.includes('medium')) return 'warn'
  if (value.includes('verified') || value.includes('fixed')) return 'ok'
  return 'info'
}
