import { useEffect, useState } from 'react'
import { API_BASE } from './api/client'
import { Analytics } from './components/Analytics'
import { CopilotPanel } from './components/CopilotPanel'
import { Shortener } from './components/Shortener'

const VIEWS = ['Shorten', 'Analytics', 'Copilot'] as const
type View = (typeof VIEWS)[number]

export default function App() {
  const [view, setView] = useState<View>('Shorten')
  // Shared so that shortening a URL pre-fills the analytics lookup.
  const [shortCode, setShortCode] = useState('')

  return (
    <div className="min-h-svh bg-slate-950">
      <header className="border-b border-slate-800">
        <div className="mx-auto flex max-w-4xl flex-wrap items-center justify-between gap-4 px-6 py-5">
          <div>
            <h1 className="text-xl font-semibold text-slate-100">URL Shortener</h1>
            <p className="text-sm text-slate-500">
              Spring Boot backend · AI Copilot engine
            </p>
          </div>
          <HealthPill />
        </div>
      </header>

      <nav className="border-b border-slate-800">
        <div className="mx-auto flex max-w-4xl gap-1 px-6">
          {VIEWS.map((name) => (
            <button
              key={name}
              onClick={() => setView(name)}
              aria-current={view === name}
              className={`-mb-px border-b-2 px-4 py-3 text-sm font-medium transition ${
                view === name
                  ? 'border-indigo-500 text-indigo-300'
                  : 'border-transparent text-slate-400 hover:text-slate-200'
              }`}
            >
              {name}
            </button>
          ))}
        </div>
      </nav>

      <main className="mx-auto max-w-4xl px-6 py-8">
        {view === 'Shorten' && <Shortener onShortened={setShortCode} />}
        {view === 'Analytics' && (
          <Analytics code={shortCode} onCodeChange={setShortCode} />
        )}
        {view === 'Copilot' && <CopilotPanel />}
      </main>
    </div>
  )
}

function HealthPill() {
  const [status, setStatus] = useState<'checking' | 'up' | 'down'>('checking')

  useEffect(() => {
    let cancelled = false
    fetch(`${API_BASE}/actuator/health`)
      .then((res) => res.json())
      .then((body: { status: string }) => {
        if (!cancelled) setStatus(body.status === 'UP' ? 'up' : 'down')
      })
      .catch(() => {
        if (!cancelled) setStatus('down')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const tone = {
    checking: 'bg-slate-500/10 text-slate-400 ring-slate-500/30',
    up: 'bg-emerald-500/10 text-emerald-300 ring-emerald-500/30',
    down: 'bg-rose-500/10 text-rose-300 ring-rose-500/30',
  }[status]

  const label = {
    checking: 'Checking API…',
    up: 'API healthy',
    down: 'API unreachable',
  }[status]

  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-medium ring-1 ring-inset ${tone}`}
    >
      <span
        aria-hidden
        className={`size-1.5 rounded-full ${
          status === 'up'
            ? 'bg-emerald-400'
            : status === 'down'
              ? 'bg-rose-400'
              : 'animate-pulse bg-slate-400'
        }`}
      />
      {label}
    </span>
  )
}
