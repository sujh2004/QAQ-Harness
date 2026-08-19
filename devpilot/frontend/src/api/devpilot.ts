import { http } from './http'
import type {
  AgentRun,
  ApiResult,
  ErrorSummary,
  LogEntry,
  LogQuery,
  Message,
  PageResponse,
  Project,
  RepositoryValidation,
  Session,
  SessionEvent,
} from './types'

/** Unwraps the common result envelope, turning a business error into a rejection. */
async function unwrap<T>(request: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const response = await request
  if (response.data.code !== 0) {
    throw new Error(response.data.message)
  }
  return response.data.data
}

/**
 * Lists projects, newest first.
 *
 * @param page zero-based page index
 * @param size page size
 */
export function listProjects(page = 0, size = 20): Promise<PageResponse<Project>> {
  return unwrap(http.get<ApiResult<PageResponse<Project>>>('/api/v1/projects', { params: { page, size } }))
}

/**
 * Reads one project.
 *
 * @param projectId project identity
 */
export function getProject(projectId: number): Promise<Project> {
  return unwrap(http.get<ApiResult<Project>>(`/api/v1/projects/${projectId}`))
}

/**
 * Checks whether the configured repository path can be read.
 *
 * @param projectId project identity
 */
export function validateRepository(projectId: number): Promise<RepositoryValidation> {
  return unwrap(
    http.post<ApiResult<RepositoryValidation>>(`/api/v1/projects/${projectId}/validate-repository`),
  )
}

/**
 * Searches the logs of a project.
 *
 * @param projectId project identity
 * @param query filters
 */
export function searchLogs(projectId: number, query: LogQuery): Promise<PageResponse<LogEntry>> {
  const params: Record<string, string | number> = {}
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params[key] = value as string | number
    }
  })
  return unwrap(
    http.get<ApiResult<PageResponse<LogEntry>>>(`/api/v1/projects/${projectId}/logs`, { params }),
  )
}

/**
 * Groups the recent errors of a project.
 *
 * @param projectId project identity
 * @param hours size of the window
 */
export function errorSummary(projectId: number, hours = 24): Promise<ErrorSummary[]> {
  return unwrap(
    http.get<ApiResult<ErrorSummary[]>>(`/api/v1/projects/${projectId}/logs/error-summary`, {
      params: { hours },
    }),
  )
}

/**
 * Lists the sessions of a project.
 *
 * @param projectId project identity
 */
export function listSessions(projectId: number): Promise<PageResponse<Session>> {
  return unwrap(http.get<ApiResult<PageResponse<Session>>>(`/api/v1/projects/${projectId}/sessions`))
}

/**
 * Opens a session.
 *
 * @param projectId project identity
 * @param title optional title
 */
export function createSession(projectId: number, title?: string): Promise<Session> {
  return unwrap(
    http.post<ApiResult<Session>>(`/api/v1/projects/${projectId}/sessions`, title ? { title } : {}),
  )
}

/**
 * Reads the chat timeline of a session.
 *
 * @param sessionId session identity
 */
export function listMessages(sessionId: string): Promise<PageResponse<Message>> {
  return unwrap(http.get<ApiResult<PageResponse<Message>>>(`/api/v1/sessions/${sessionId}/messages`))
}

/**
 * Replays the committed events of a session.
 *
 * @param sessionId session identity
 * @param afterSeq exclusive lower bound
 */
export function listEvents(sessionId: string, afterSeq = 0): Promise<SessionEvent[]> {
  return unwrap(
    http.get<ApiResult<SessionEvent[]>>(`/api/v1/sessions/${sessionId}/events`, {
      params: { afterSeq, limit: 500 },
    }),
  )
}

/**
 * Reads the agent run tree of a session.
 *
 * @param sessionId session identity
 */
export function listRuns(sessionId: string): Promise<AgentRun[]> {
  return unwrap(http.get<ApiResult<AgentRun[]>>(`/api/v1/sessions/${sessionId}/runs`))
}
