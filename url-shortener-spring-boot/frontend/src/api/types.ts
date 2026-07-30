/**
 * Mirrors the backend DTOs in com.copilot.urlshortener.dto.
 *
 * Note the two different JSON naming conventions on the backend:
 *  - url/* DTOs map camelCase fields to snake_case via @JsonProperty
 *  - copilot/* DTOs declare snake_case fields directly (no @JsonProperty)
 * Both land on snake_case over the wire, which is what these types describe.
 */

export interface ShortenRequest {
  original_url: string
  custom_alias?: string
  /** LocalDateTime — must be sent without a timezone suffix, e.g. 2027-01-01T00:00:00 */
  expires_at?: string
}

export interface ShortenResponse {
  id: number
  original_url: string
  short_code: string
  short_url: string
  created_at: string
  expires_at: string | null
}

export interface AnalyticsResponse {
  short_code: string
  original_url: string
  click_count: number
  created_at: string
  expires_at: string | null
  /**
   * Computed server-side against the server clock. Trust this rather than
   * comparing expires_at locally: the backend sends a zone-less LocalDateTime,
   * so a client-side comparison is wrong for any user outside the server's
   * timezone.
   */
  expired: boolean
  last_clicked_at: string | null
}

/**
 * GlobalExceptionHandler returns this shape for every non-2xx response.
 *
 * `detail` is always safe to show a user. 5xx responses carry `error_id`
 * instead of a cause — the cause stays in the server log, and the id is what
 * correlates a user's report to it.
 */
export interface ErrorResponse {
  detail: string
  /** Present on 5xx only. Show it, so a user can quote it in a bug report. */
  error_id?: string
  /** Present on validation failures: field name to message. */
  field_errors?: Record<string, string>
}

// ---------------------------------------------------------------- copilot

export interface FunctionalRequirement {
  id: string
  description: string
}

export interface NonFunctionalRequirement {
  category: string
  description: string
}

export interface RequirementAnalysis {
  functional_requirements: FunctionalRequirement[]
  non_functional_requirements: NonFunctionalRequirement[]
  ambiguities: string[]
  assumptions: string[]
}

export interface EngineeringTask {
  id: string
  title: string
  description: string
  depends_on: string[]
  ai_assistance: string
}

export interface ArtifactFile {
  path: string
  description: string
}

export interface EngineeringArtifacts {
  folder_structure: string[]
  database_schema: string
  api_contracts: string[]
  key_files: ArtifactFile[]
}

export interface ValidationFinding {
  area: string
  finding: string
  severity: string
}

export interface ValidationReport {
  code_review: ValidationFinding[]
  security_review: ValidationFinding[]
  performance_review: ValidationFinding[]
  missing_edge_cases: string[]
  test_coverage_summary: string
}

export interface Risk {
  category: string
  risk: string
  mitigation: string
}

export interface RiskAnalysis {
  risks: Risk[]
}

export interface FinalSummary {
  implementation_approach: string
  generated_artifacts: string[]
  risks_and_validation: string
  assumptions_and_limitations: string
}

export interface CopilotResponse {
  requirement_analysis: RequirementAnalysis
  task_decomposition: EngineeringTask[]
  engineering_artifacts: EngineeringArtifacts
  validation: ValidationReport
  risk_analysis: RiskAnalysis
  final_summary: FinalSummary
}
