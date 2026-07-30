import type {
  AnalyticsResponse,
  CopilotResponse,
  ErrorResponse,
  ShortenRequest,
  ShortenResponse,
} from './types'

export const API_BASE =
  import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'

/** Thrown for any non-2xx response, carrying the backend's `detail` message. */
export class ApiError extends Error {
  readonly status: number
  /** Correlation id for 5xx responses; worth surfacing so users can quote it. */
  readonly errorId?: string
  /** Field-level messages for validation failures (422). */
  readonly fieldErrors?: Record<string, string>

  constructor(
    status: number,
    message: string,
    options?: { errorId?: string; fieldErrors?: Record<string, string> },
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorId = options?.errorId
    this.fieldErrors = options?.fieldErrors
  }

  /** Message plus the correlation id, when there is one worth showing. */
  get displayMessage(): string {
    return this.errorId ? `${this.message} (ref: ${this.errorId})` : this.message
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })
  } catch {
    // fetch only rejects on network/CORS failure, never on a 4xx/5xx.
    throw new ApiError(0, `Cannot reach the API at ${API_BASE}. Is the backend running?`)
  }

  if (!res.ok) {
    // GlobalExceptionHandler returns { detail } for every error status, but a
    // proxy or a crash before the handler could still yield non-JSON.
    const body = await res.json().catch(() => null as ErrorResponse | null)

    // A 422 names the offending fields; flatten them into the message so the
    // user sees what to fix rather than a bare "Validation failed".
    const fieldSummary = body?.field_errors
      ? Object.values(body.field_errors).join('; ')
      : undefined

    const message =
      fieldSummary || body?.detail || res.statusText || `Request failed with ${res.status}`

    throw new ApiError(res.status, message, {
      errorId: body?.error_id,
      fieldErrors: body?.field_errors,
    })
  }

  return res.json() as Promise<T>
}

export function shortenUrl(body: ShortenRequest): Promise<ShortenResponse> {
  return request<ShortenResponse>('/shorten', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function getAnalytics(shortCode: string): Promise<AnalyticsResponse> {
  return request<AnalyticsResponse>(`/analytics/${encodeURIComponent(shortCode)}`)
}

export function analyzeRequirement(requirement: string): Promise<CopilotResponse> {
  return request<CopilotResponse>('/copilot/analyze', {
    method: 'POST',
    body: JSON.stringify({ requirement }),
  })
}
