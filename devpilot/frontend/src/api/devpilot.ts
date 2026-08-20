import { http } from './http'
import type {
  AgentRun,
  ApiResult,
  ErrorSummary,
  KnowledgeDocument,
  KnowledgeMatch,
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
      params: { afterSeq, limit: EVENT_PAGE_SIZE },
    }),
  )
}

/** How many events one replay request returns. */
const EVENT_PAGE_SIZE = 500

/**
 * Replays a session's whole event stream.
 *
 * <p>Paging to the end matters: a partially replayed stream would show a timeline that silently
 * differs from what the live view showed, which is exactly the inconsistency event sourcing exists
 * to prevent.
 *
 * @param sessionId session identity
 */
export async function listAllEvents(sessionId: string): Promise<SessionEvent[]> {
  const all: SessionEvent[] = []
  let afterSeq = 0
  for (;;) {
    const page = await listEvents(sessionId, afterSeq)
    all.push(...page)
    const last = page.length > 0 ? page[page.length - 1] : undefined
    if (!last || page.length < EVENT_PAGE_SIZE) {
      return all
    }
    afterSeq = last.seq
  }
}

/**
 * Reads the agent run tree of a session.
 *
 * @param sessionId session identity
 */
export function listRuns(sessionId: string): Promise<AgentRun[]> {
  return unwrap(http.get<ApiResult<AgentRun[]>>(`/api/v1/sessions/${sessionId}/runs`))
}

/**
 * Cancels the running turn of a session.
 *
 * @param sessionId session identity
 * @param turnId turn to cancel
 */
export function cancelTurn(sessionId: string, turnId: string): Promise<unknown> {
  return unwrap(
    http.post<ApiResult<unknown>>(`/api/v1/sessions/${sessionId}/turns/${turnId}/cancel`),
  )
}

/**
 * Lists the knowledge documents of a project.
 *
 * @param projectId project identity
 */
export function listKnowledge(projectId: number): Promise<KnowledgeDocument[]> {
  return unwrap(
    http.get<ApiResult<KnowledgeDocument[]>>(`/api/v1/projects/${projectId}/knowledge`),
  )
}

/**
 * Imports and indexes one document.
 *
 * @param projectId project identity
 * @param document name, type and content
 */
export function uploadKnowledge(
  projectId: number,
  document: { documentName: string; documentType?: string; sourcePath?: string; content: string },
): Promise<KnowledgeDocument> {
  return unwrap(
    http.post<ApiResult<KnowledgeDocument>>(
      `/api/v1/projects/${projectId}/knowledge/upload`,
      document,
      // Embedding a whole document takes longer than a plain read.
      { timeout: 120_000 },
    ),
  )
}

/**
 * Rebuilds the whole index of a project.
 *
 * @param projectId project identity
 */
export function reindexKnowledge(projectId: number): Promise<KnowledgeDocument[]> {
  return unwrap(
    http.post<ApiResult<KnowledgeDocument[]>>(`/api/v1/projects/${projectId}/knowledge/reindex`, null, {
      timeout: 300_000,
    }),
  )
}

/**
 * Retrieves passages relevant to a query.
 *
 * @param projectId project identity
 * @param query natural-language query
 * @param topK how many passages to return
 */
export function searchKnowledge(
  projectId: number,
  query: string,
  topK = 5,
): Promise<KnowledgeMatch[]> {
  return unwrap(
    http.get<ApiResult<KnowledgeMatch[]>>(`/api/v1/projects/${projectId}/knowledge/search`, {
      params: { query, topK },
      timeout: 60_000,
    }),
  )
}

/**
 * Removes one document and rebuilds the index without it.
 *
 * @param projectId project identity
 * @param documentId document to remove
 */
export function deleteKnowledge(projectId: number, documentId: number): Promise<unknown> {
  return unwrap(
    http.delete<ApiResult<unknown>>(`/api/v1/projects/${projectId}/knowledge/${documentId}`, {
      timeout: 300_000,
    }),
  )
}
