import { useState } from 'react'
import { analyzeRequirement, ApiError } from '../api/client'
import type { CopilotResponse, ValidationFinding } from '../api/types'
import { severityTone } from '../lib/format'
import {
  Badge,
  BulletList,
  Button,
  Card,
  Empty,
  ErrorBanner,
  SectionTitle,
  inputClass,
} from './ui'

const SAMPLE =
  'Add rate limiting to the shorten endpoint so a client cannot create more than 100 links per hour'

const TABS = [
  'Requirements',
  'Tasks',
  'Artifacts',
  'Validation',
  'Risks',
  'Summary',
] as const

type Tab = (typeof TABS)[number]

export function CopilotPanel() {
  const [requirement, setRequirement] = useState('')
  const [data, setData] = useState<CopilotResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [tab, setTab] = useState<Tab>('Requirements')

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setLoading(true)
    setError(null)
    try {
      setData(await analyzeRequirement(requirement.trim()))
      setTab('Requirements')
    } catch (err) {
      setError(err instanceof ApiError ? err.displayMessage : String(err))
      setData(null)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      <Card>
        <form onSubmit={handleSubmit} className="space-y-3">
          <label className="block text-sm font-medium text-slate-300">
            Requirement
          </label>
          <textarea
            required
            rows={3}
            minLength={10}
            value={requirement}
            onChange={(e) => setRequirement(e.target.value)}
            placeholder="Describe a feature or change in plain English (at least 10 characters)…"
            className={`${inputClass} resize-y`}
          />
          <div className="flex flex-wrap items-center gap-3">
            <Button type="submit" loading={loading}>
              {loading ? 'Analysing…' : 'Analyse requirement'}
            </Button>
            <button
              type="button"
              onClick={() => setRequirement(SAMPLE)}
              className="text-sm text-slate-400 underline-offset-4 hover:text-slate-200 hover:underline"
            >
              Use sample requirement
            </button>
          </div>
        </form>
      </Card>

      {error && <ErrorBanner message={error} />}

      {data && (
        <>
          <div
            role="tablist"
            className="flex flex-wrap gap-1 rounded-lg border border-slate-800 bg-slate-900/60 p-1"
          >
            {TABS.map((name) => (
              <button
                key={name}
                role="tab"
                aria-selected={tab === name}
                onClick={() => setTab(name)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
                  tab === name
                    ? 'bg-indigo-500 text-white'
                    : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                }`}
              >
                {name}
              </button>
            ))}
          </div>

          <div role="tabpanel" className="space-y-4">
            {tab === 'Requirements' && <RequirementsTab data={data} />}
            {tab === 'Tasks' && <TasksTab data={data} />}
            {tab === 'Artifacts' && <ArtifactsTab data={data} />}
            {tab === 'Validation' && <ValidationTab data={data} />}
            {tab === 'Risks' && <RisksTab data={data} />}
            {tab === 'Summary' && <SummaryTab data={data} />}
          </div>
        </>
      )}
    </div>
  )
}

function RequirementsTab({ data }: { data: CopilotResponse }) {
  const analysis = data.requirement_analysis
  return (
    <>
      <Card>
        <SectionTitle count={analysis.functional_requirements.length}>
          Functional requirements
        </SectionTitle>
        <ul className="space-y-2">
          {analysis.functional_requirements.map((item) => (
            <li key={item.id} className="flex gap-3 text-sm">
              <Badge tone="info">{item.id}</Badge>
              <span className="text-slate-300">{item.description}</span>
            </li>
          ))}
        </ul>
      </Card>

      <Card>
        <SectionTitle count={analysis.non_functional_requirements.length}>
          Non-functional requirements
        </SectionTitle>
        <ul className="space-y-2">
          {analysis.non_functional_requirements.map((item, i) => (
            <li key={i} className="flex gap-3 text-sm">
              <Badge>{item.category}</Badge>
              <span className="text-slate-300">{item.description}</span>
            </li>
          ))}
        </ul>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <SectionTitle count={analysis.ambiguities.length}>Ambiguities</SectionTitle>
          <BulletList items={analysis.ambiguities} />
        </Card>
        <Card>
          <SectionTitle count={analysis.assumptions.length}>Assumptions</SectionTitle>
          <BulletList items={analysis.assumptions} />
        </Card>
      </div>
    </>
  )
}

function TasksTab({ data }: { data: CopilotResponse }) {
  return (
    <Card>
      <SectionTitle count={data.task_decomposition.length}>
        Task decomposition
      </SectionTitle>
      <ol className="space-y-3">
        {data.task_decomposition.map((task) => (
          <li
            key={task.id}
            className="rounded-lg border border-slate-800 bg-slate-950/50 p-4"
          >
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="info">{task.id}</Badge>
              <h4 className="font-medium text-slate-100">{task.title}</h4>
              {task.depends_on.length > 0 && (
                <span className="text-xs text-slate-500">
                  depends on {task.depends_on.join(', ')}
                </span>
              )}
            </div>
            <p className="mt-2 text-sm text-slate-300">{task.description}</p>
            <p className="mt-2 border-l-2 border-indigo-500/40 pl-3 text-sm text-slate-400">
              <span className="font-medium text-indigo-300">AI assistance: </span>
              {task.ai_assistance}
            </p>
          </li>
        ))}
      </ol>
    </Card>
  )
}

function ArtifactsTab({ data }: { data: CopilotResponse }) {
  const artifacts = data.engineering_artifacts
  return (
    <>
      <Card>
        <SectionTitle>Database schema</SectionTitle>
        <p className="rounded-lg bg-slate-950/70 p-3 font-mono text-xs leading-relaxed text-slate-300">
          {artifacts.database_schema}
        </p>
      </Card>

      <Card>
        <SectionTitle count={artifacts.api_contracts.length}>API contracts</SectionTitle>
        <ul className="space-y-1.5">
          {artifacts.api_contracts.map((contract, i) => (
            <li
              key={i}
              className="rounded-md bg-slate-950/70 px-3 py-2 font-mono text-xs break-all text-slate-300"
            >
              {contract}
            </li>
          ))}
        </ul>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <SectionTitle count={artifacts.folder_structure.length}>
            Folder structure
          </SectionTitle>
          <ul className="space-y-1 font-mono text-xs text-slate-400">
            {artifacts.folder_structure.map((path, i) => (
              <li key={i} className="break-all">
                {path}
              </li>
            ))}
          </ul>
        </Card>

        <Card>
          <SectionTitle count={artifacts.key_files.length}>Key files</SectionTitle>
          <ul className="space-y-2">
            {artifacts.key_files.map((file, i) => (
              <li key={i}>
                <p className="font-mono text-xs break-all text-slate-200">{file.path}</p>
                <p className="text-sm text-slate-400">{file.description}</p>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </>
  )
}

function FindingList({ findings }: { findings: ValidationFinding[] }) {
  if (!findings?.length) return <Empty>No findings.</Empty>
  return (
    <ul className="space-y-2">
      {findings.map((finding, i) => (
        <li
          key={i}
          className="rounded-lg border border-slate-800 bg-slate-950/50 p-3"
        >
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs text-slate-200">{finding.area}</span>
            <Badge tone={severityTone(finding.severity)}>{finding.severity}</Badge>
          </div>
          <p className="mt-1.5 text-sm text-slate-300">{finding.finding}</p>
        </li>
      ))}
    </ul>
  )
}

function ValidationTab({ data }: { data: CopilotResponse }) {
  const validation = data.validation
  return (
    <>
      <Card>
        <SectionTitle count={validation.code_review.length}>Code review</SectionTitle>
        <FindingList findings={validation.code_review} />
      </Card>
      <Card>
        <SectionTitle count={validation.security_review.length}>
          Security review
        </SectionTitle>
        <FindingList findings={validation.security_review} />
      </Card>
      <Card>
        <SectionTitle count={validation.performance_review.length}>
          Performance review
        </SectionTitle>
        <FindingList findings={validation.performance_review} />
      </Card>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <SectionTitle count={validation.missing_edge_cases.length}>
            Missing edge cases
          </SectionTitle>
          <BulletList items={validation.missing_edge_cases} />
        </Card>
        <Card>
          <SectionTitle>Test coverage</SectionTitle>
          <p className="text-sm text-slate-300">{validation.test_coverage_summary}</p>
        </Card>
      </div>
    </>
  )
}

function RisksTab({ data }: { data: CopilotResponse }) {
  return (
    <Card>
      <SectionTitle count={data.risk_analysis.risks.length}>Risk analysis</SectionTitle>
      <ul className="space-y-3">
        {data.risk_analysis.risks.map((risk, i) => (
          <li
            key={i}
            className="rounded-lg border border-slate-800 bg-slate-950/50 p-4"
          >
            <Badge tone={risk.category === 'AI-related' ? 'warn' : 'info'}>
              {risk.category}
            </Badge>
            <p className="mt-2 text-sm font-medium text-slate-100">{risk.risk}</p>
            <p className="mt-1.5 border-l-2 border-emerald-500/40 pl-3 text-sm text-slate-400">
              <span className="font-medium text-emerald-300">Mitigation: </span>
              {risk.mitigation}
            </p>
          </li>
        ))}
      </ul>
    </Card>
  )
}

function SummaryTab({ data }: { data: CopilotResponse }) {
  const summary = data.final_summary
  return (
    <>
      <Card>
        <SectionTitle>Implementation approach</SectionTitle>
        <p className="text-sm text-slate-300">{summary.implementation_approach}</p>
      </Card>
      <Card>
        <SectionTitle count={summary.generated_artifacts.length}>
          Generated artifacts
        </SectionTitle>
        <BulletList items={summary.generated_artifacts} />
      </Card>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <SectionTitle>Risks &amp; validation</SectionTitle>
          <p className="text-sm text-slate-300">{summary.risks_and_validation}</p>
        </Card>
        <Card>
          <SectionTitle>Assumptions &amp; limitations</SectionTitle>
          <p className="text-sm text-slate-300">{summary.assumptions_and_limitations}</p>
        </Card>
      </div>
    </>
  )
}
