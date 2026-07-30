import type { ReactNode } from 'react'

export function Card({
  children,
  className = '',
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div
      className={`rounded-xl border border-slate-800 bg-slate-900/60 p-5 ${className}`}
    >
      {children}
    </div>
  )
}

export function SectionTitle({
  children,
  count,
}: {
  children: ReactNode
  count?: number
}) {
  return (
    <h3 className="mb-3 flex items-center gap-2 text-xs font-semibold tracking-wider text-slate-400 uppercase">
      {children}
      {count !== undefined && (
        <span className="rounded-full bg-slate-800 px-2 py-0.5 text-[11px] font-medium text-slate-400 normal-case">
          {count}
        </span>
      )}
    </h3>
  )
}

const TONES = {
  danger: 'bg-rose-500/10 text-rose-300 ring-rose-500/30',
  warn: 'bg-amber-500/10 text-amber-300 ring-amber-500/30',
  info: 'bg-sky-500/10 text-sky-300 ring-sky-500/30',
  ok: 'bg-emerald-500/10 text-emerald-300 ring-emerald-500/30',
  neutral: 'bg-slate-500/10 text-slate-300 ring-slate-500/30',
} as const

export function Badge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode
  tone?: keyof typeof TONES
}) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${TONES[tone]}`}
    >
      {children}
    </span>
  )
}

export function Button({
  children,
  loading = false,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { loading?: boolean }) {
  return (
    <button
      {...props}
      disabled={props.disabled || loading}
      className="inline-flex items-center justify-center gap-2 rounded-lg bg-indigo-500 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-400 focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-slate-950 focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
    >
      {loading && (
        <span
          aria-hidden
          className="size-3.5 animate-spin rounded-full border-2 border-white/30 border-t-white"
        />
      )}
      {children}
    </button>
  )
}

export function Field({
  label,
  hint,
  children,
}: {
  label: string
  hint?: string
  children: ReactNode
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-slate-300">
        {label}
      </span>
      {children}
      {hint && <span className="mt-1 block text-xs text-slate-500">{hint}</span>}
    </label>
  )
}

export const inputClass =
  'w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-600 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 focus:outline-none'

export function ErrorBanner({ message }: { message: string }) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-200"
    >
      {message}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="text-sm text-slate-500 italic">{children}</p>
}

/** Renders a list of plain strings as bullets, or an empty note. */
export function BulletList({ items }: { items: string[] }) {
  if (!items?.length) return <Empty>None reported.</Empty>
  return (
    <ul className="space-y-1.5">
      {items.map((item, i) => (
        <li key={i} className="flex gap-2 text-sm text-slate-300">
          <span aria-hidden className="mt-2 size-1 shrink-0 rounded-full bg-slate-600" />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  )
}
