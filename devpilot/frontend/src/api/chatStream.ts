import type { ChatStreamRequest, SessionEvent } from './types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * Consumes the chat stream of one session.
 *
 * <p>SSE is a projection of the committed event log, so a frame is only ever a fact that is already
 * durable. Two consequences shape this client: the caller may drop any event it has already seen —
 * the sequence number is the identity — and after a dropped connection it resumes by sending the
 * last sequence number it holds as `Last-Event-ID`, which the server answers by replaying exactly
 * what was missed.
 *
 * <p>EventSource cannot be used because it only issues GET requests and cannot carry a body or a
 * custom header, so the stream is read from `fetch` directly.
 *
 * @param request project, session and question; omit the message to attach without asking anything
 * @param lastEventId highest sequence number already consumed, null for a fresh stream
 * @param onEvent called once per event, in sequence order
 * @param signal aborts the stream when the caller navigates away or cancels
 */
export async function streamChat(
  request: ChatStreamRequest,
  lastEventId: number | null,
  onEvent: (event: SessionEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    // A rejected request answers with JSON, so both shapes have to be acceptable — asking only for
    // the stream would turn a readable error into a bare 406.
    Accept: 'text/event-stream, application/json',
  }
  if (lastEventId !== null && lastEventId > 0) {
    headers['Last-Event-ID'] = String(lastEventId)
  }

  const response = await fetch(`${BASE_URL}/api/v1/chat/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(request),
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(await readError(response))
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })

    // Frames are separated by a blank line; anything after the last one is a partial frame.
    let boundary = buffer.indexOf('\n\n')
    while (boundary !== -1) {
      const frame = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const event = parseFrame(frame)
      if (event) {
        onEvent(event)
      }
      boundary = buffer.indexOf('\n\n')
    }
  }
}

/**
 * Turns one raw SSE frame into an event.
 *
 * @param frame text of a single frame, without the trailing blank line
 * @returns the decoded event, or null for keep-alive comments and unparsable frames
 */
function parseFrame(frame: string): SessionEvent | null {
  const data: string[] = []
  for (const rawLine of frame.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith(':')) {
      // A keep-alive comment: it holds the connection open and carries no fact.
      continue
    }
    if (line.startsWith('data:')) {
      data.push(line.slice(5).trimStart())
    }
  }
  if (data.length === 0) {
    return null
  }
  try {
    return JSON.parse(data.join('\n')) as SessionEvent
  } catch {
    return null
  }
}

/**
 * Reads the failure message of a non-streaming error response.
 *
 * @param response failed response
 * @returns message safe to show the user
 */
async function readError(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string }
    if (body.message) {
      return body.message
    }
  } catch {
    // Fall through to the status line.
  }
  return `对话流请求失败（HTTP ${response.status}）`
}
