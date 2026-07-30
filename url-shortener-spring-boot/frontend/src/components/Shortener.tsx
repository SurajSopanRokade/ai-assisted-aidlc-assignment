import { useState } from 'react'
import { ApiError, shortenUrl } from '../api/client'
import type { ShortenRequest, ShortenResponse } from '../api/types'
import { formatDateTime } from '../lib/format'
import { Badge, Button, Card, ErrorBanner, Field, inputClass } from './ui'

export function Shortener({
  onShortened,
}: {
  onShortened: (code: string) => void
}) {
  const [originalUrl, setOriginalUrl] = useState('')
  const [customAlias, setCustomAlias] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [result, setResult] = useState<ShortenResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setLoading(true)
    setError(null)
    setResult(null)
    setCopied(false)

    const body: ShortenRequest = { original_url: originalUrl.trim() }
    if (customAlias.trim()) body.custom_alias = customAlias.trim()
    // <input type="datetime-local"> yields "YYYY-MM-DDTHH:mm", which is already
    // the LocalDateTime format Jackson expects — no timezone suffix.
    if (expiresAt) body.expires_at = `${expiresAt}:00`

    try {
      const response = await shortenUrl(body)
      setResult(response)
      onShortened(response.short_code)
    } catch (err) {
      setError(err instanceof ApiError ? err.displayMessage : String(err))
    } finally {
      setLoading(false)
    }
  }

  async function copyShortUrl() {
    if (!result) return
    await navigator.clipboard.writeText(result.short_url)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div className="space-y-4">
      <Card>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Original URL" hint="Must start with http:// or https:// (max 2048 characters).">
            <input
              required
              type="text"
              value={originalUrl}
              onChange={(e) => setOriginalUrl(e.target.value)}
              placeholder="https://example.com/a/very/long/path"
              className={inputClass}
            />
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Custom alias (optional)" hint="3–16 alphanumeric characters.">
              <input
                type="text"
                value={customAlias}
                onChange={(e) => setCustomAlias(e.target.value)}
                placeholder="mylink"
                className={inputClass}
              />
            </Field>

            <Field label="Expires at (optional)" hint="Leave blank for a permanent link.">
              <input
                type="datetime-local"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
                className={inputClass}
              />
            </Field>
          </div>

          <Button type="submit" loading={loading}>
            {loading ? 'Shortening…' : 'Shorten URL'}
          </Button>
        </form>
      </Card>

      {error && <ErrorBanner message={error} />}

      {result && (
        <Card className="border-emerald-500/30 bg-emerald-500/5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="mb-1 text-xs font-semibold tracking-wider text-emerald-400 uppercase">
                Short link created
              </p>
              {/*
                Rendered as a plain anchor, never fetched: GET /{shortCode}
                returns a 307 to an arbitrary external origin, which XHR/fetch
                cannot follow under CORS.
              */}
              <a
                href={result.short_url}
                target="_blank"
                rel="noreferrer"
                className="font-mono text-lg break-all text-emerald-300 underline-offset-4 hover:underline"
              >
                {result.short_url}
              </a>
            </div>
            <Button type="button" onClick={copyShortUrl}>
              {copied ? 'Copied' : 'Copy'}
            </Button>
          </div>

          <dl className="mt-4 grid gap-x-6 gap-y-2 border-t border-emerald-500/20 pt-4 text-sm sm:grid-cols-2">
            <Row label="Short code">
              <span className="font-mono">{result.short_code}</span>
            </Row>
            <Row label="Created">{formatDateTime(result.created_at)}</Row>
            <Row label="Destination">
              <span className="break-all">{result.original_url}</span>
            </Row>
            <Row label="Expires">
              {result.expires_at ? (
                formatDateTime(result.expires_at)
              ) : (
                <Badge tone="ok">Never</Badge>
              )}
            </Row>
          </dl>
        </Card>
      )}
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="text-slate-200">{children}</dd>
    </div>
  )
}
