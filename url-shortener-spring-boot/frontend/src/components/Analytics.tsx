import { useState } from 'react'
import { ApiError, getAnalytics } from '../api/client'
import type { AnalyticsResponse } from '../api/types'
import { formatDateTime, formatRelative } from '../lib/format'
import { Button, Card, Empty, ErrorBanner, inputClass } from './ui'

export function Analytics({
  code,
  onCodeChange,
}: {
  code: string
  onCodeChange: (code: string) => void
}) {
  const [data, setData] = useState<AnalyticsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setLoading(true)
    setError(null)
    setData(null)
    try {
      setData(await getAnalytics(code.trim()))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }

  const lastClicked = formatRelative(data?.last_clicked_at)

  return (
    <div className="space-y-4">
      <Card>
        <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3">
          <div className="min-w-56 flex-1">
            <label className="mb-1.5 block text-sm font-medium text-slate-300">
              Short code
            </label>
            <input
              required
              type="text"
              value={code}
              onChange={(e) => onCodeChange(e.target.value)}
              placeholder="2B6V5rm"
              className={`${inputClass} font-mono`}
            />
          </div>
          <Button type="submit" loading={loading}>
            {loading ? 'Loading…' : 'Look up'}
          </Button>
        </form>
      </Card>

      {error && <ErrorBanner message={error} />}

      {data && (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Stat label="Total clicks" value={String(data.click_count)} />
            <Stat label="Created" value={formatDateTime(data.created_at)} />
            <Stat
              label="Last clicked"
              value={data.last_clicked_at ? formatDateTime(data.last_clicked_at) : 'Never'}
              sub={lastClicked ?? undefined}
            />
          </div>

          <Card>
            <p className="text-xs text-slate-500">Destination</p>
            <a
              href={data.original_url}
              target="_blank"
              rel="noreferrer"
              className="break-all text-slate-200 underline-offset-4 hover:text-indigo-300 hover:underline"
            >
              {data.original_url}
            </a>
            {data.click_count === 0 && (
              <div className="mt-3">
                <Empty>This link has not been clicked yet.</Empty>
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  )
}

function Stat({
  label,
  value,
  sub,
}: {
  label: string
  value: string
  sub?: string
}) {
  return (
    <Card>
      <p className="text-xs tracking-wider text-slate-500 uppercase">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-100 tabular-nums">
        {value}
      </p>
      {sub && <p className="mt-0.5 text-xs text-slate-500">{sub}</p>}
    </Card>
  )
}
