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

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
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
    const detail = await res
      .json()
      .then((body: ErrorResponse) => body.detail)
      .catch(() => res.statusText)
    throw new ApiError(res.status, detail || `Request failed with ${res.status}`)
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
