/** Shared response shapes of the DevPilot backend. */

/** Envelope returned by every non-streaming endpoint. */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** One page of results. */
export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

/** A project and the local repository it is bound to. */
export interface Project {
  id: number
  name: string
  code: string
  description: string | null
  repositoryPath: string
  defaultBranch: string
  status: number
  createdAt: string
  updatedAt: string
}

/** Outcome of checking a configured repository path. */
export interface RepositoryValidation {
  repositoryPath: string
  resolvedPath: string
  exists: boolean
  directory: boolean
  readable: boolean
  gitRepository: boolean
  accessible: boolean
  detail: string
}

/** One recorded log line. */
export interface LogEntry {
  id: number
  projectId: number
  serviceName: string
  level: string
  traceId: string | null
  logger: string | null
  message: string
  exceptionType: string | null
  stackTrace: string | null
  logTime: string
}

/** One group of recent errors. */
export interface ErrorSummary {
  serviceName: string
  exceptionType: string | null
  occurrences: number
  firstSeen: string
  lastSeen: string
  sampleMessage: string | null
}

/** A conversation and its runtime event stream. */
export interface Session {
  sessionId: string
  projectId: number
  title: string
  createdAt: string
  updatedAt: string
}

/** One message of the chat timeline. */
export interface Message {
  sessionId: string
  seq: number
  role: string
  content: string
  createdAt: string
}

/** One committed runtime event, as pushed over SSE and replayed over HTTP. */
export interface SessionEvent {
  sessionId: string
  seq: number
  occurredAt: string
  eventType: string
  schemaVersion: number
  turnId: string | null
  stepId: string | null
  runId: string | null
  callId: string | null
  payload: Record<string, unknown>
}

/** One agent run, projected from the event stream. */
export interface AgentRun {
  runId: string
  turnId: string
  parentRunId: string | null
  agentName: string
  displayName: string
  status: string
  outputSummary: string | null
  errorMessage: string | null
  startedAt: string
  endedAt: string | null
}

/** Filters accepted by the log search endpoint. */
export interface LogQuery {
  serviceName?: string
  level?: string
  keyword?: string
  traceId?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}

/** One imported knowledge document. */
export interface KnowledgeDocument {
  id: number
  projectId: number
  documentName: string
  documentType: string | null
  sourcePath: string | null
  vectorStatus: string
  chunkCount: number
  createdAt: string
}

/** One retrieved passage, with the document it came from. */
export interface KnowledgeMatch {
  documentId: number
  documentName: string
  documentType: string
  chunk: string
  score: number
}

/** Body of a streaming chat request. */
export interface ChatStreamRequest {
  projectId: number
  sessionId?: string
  message?: string
}

/** One test case an agent designed and stored. */
export interface TestCase {
  id: number
  projectId: number
  sessionId: string | null
  title: string
  priority: string | null
  precondition: string | null
  steps: string[]
  expectedResult: string
  source: string
  createdAt: string
}

/** One package offered by the marketplace. Browsing installs nothing. */
export interface SkillPackage {
  key: string
  name: string
  version: string
  description: string | null
  runtime: string
  entrypoint: string
  argsSchema: Record<string, unknown>
  files: Record<string, string>
}

/** One skill installed on this deployment. */
export interface InstalledSkill {
  id: number
  skillKey: string
  name: string
  version: string
  description: string | null
  runtime: string
  entrypoint: string
  sourceUrl: string | null
  checksum: string
  status: string
  installedAt: string
}

