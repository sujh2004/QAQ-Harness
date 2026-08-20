import { computed, reactive, ref } from 'vue'

import type { SessionEvent } from '@/api/types'

/** One tool call, as it appears inside an agent run. */
export interface ToolCallNode {
  callId: string
  toolName: string
  requestSummary: string
  status: string
  message: string
  resultSummary: string
  durationMs: number | null
}

/** An agent run and, in the order they happened, the tool calls and delegations inside it. */
export interface RunNode {
  runId: string
  parentRunId: string | null
  agentName: string
  displayName: string
  inputSummary: string
  status: string
  outputSummary: string
  errorMessage: string
  items: RunItem[]
}

/** Something that happened inside a run: a tool call, or a specialist the run delegated to. */
export type RunItem = { kind: 'tool'; tool: ToolCallNode } | { kind: 'run'; run: RunNode }

/** One bubble of the chat thread. */
export interface ChatMessage {
  seq: number
  role: 'user' | 'assistant'
  content: string
  agentName: string
}

/**
 * Folds a session's events into what the page shows.
 *
 * <p>The event stream is the only input: the same reducer produces the timeline whether events
 * arrive live, are replayed after a reconnect, or are read back on a fresh page load. That is what
 * makes a refresh show the same thing the live view showed.
 *
 * <p>Consumption is idempotent by sequence number rather than by arrangement — a reconnect may
 * legitimately resend an event, and rendering it twice would be a bug the user can see.
 */
export function useSessionTimeline() {
  const consumed = new Set<number>()
  // Holds the same reactive proxies that are rendered, not the raw objects behind them: mutating a
  // raw object would change what is on screen without telling Vue to redraw it.
  const runsById = new Map<string, RunNode>()

  const messages = ref<ChatMessage[]>([])
  const rootRuns = ref<RunNode[]>([])
  const lastSeq = ref(0)
  const activeTurnId = ref<string | null>(null)
  const turnStatus = ref<string>('')
  const runtimeErrors = ref<string[]>([])

  const turnRunning = computed(() => activeTurnId.value !== null)

  /** Forgets everything, for switching to another session. */
  function reset() {
    consumed.clear()
    runsById.clear()
    messages.value = []
    rootRuns.value = []
    lastSeq.value = 0
    activeTurnId.value = null
    turnStatus.value = ''
    runtimeErrors.value = []
  }

  /**
   * Applies one committed event.
   *
   * @param event event as it arrived over SSE or from the replay endpoint
   */
  function apply(event: SessionEvent) {
    if (consumed.has(event.seq)) {
      return
    }
    consumed.add(event.seq)
    lastSeq.value = Math.max(lastSeq.value, event.seq)

    const payload = event.payload as Record<string, unknown>
    switch (event.eventType) {
      case 'turn_started':
        activeTurnId.value = event.turnId
        turnStatus.value = 'RUNNING'
        break
      case 'turn_ended':
        activeTurnId.value = null
        turnStatus.value = text(payload.status) || 'COMPLETED'
        break
      case 'user_message':
        messages.value.push({
          seq: event.seq,
          role: 'user',
          content: text(payload.content),
          agentName: '',
        })
        break
      case 'assistant_message':
        appendAssistantMessage(event, payload)
        break
      case 'agent_started':
        startRun(event, payload)
        break
      case 'agent_finished':
        finishRun(event, payload)
        break
      case 'tool_call_requested':
        startToolCall(event, payload)
        break
      case 'tool_call_finished':
        finishToolCall(event, payload)
        break
      case 'runtime_error':
        runtimeErrors.value.push(text(payload.message) || '运行时错误')
        break
      default:
        break
    }
  }

  /**
   * Adds an assistant bubble, but only for the agent the user is talking to.
   *
   * <p>Specialists answer too; their answers are evidence the supervisor consumed, not replies to
   * the user. They stay in the trajectory panel, where their provenance is visible.
   */
  function appendAssistantMessage(event: SessionEvent, payload: Record<string, unknown>) {
    const run = event.runId ? runsById.get(event.runId) : undefined
    if (run && run.parentRunId) {
      return
    }
    messages.value.push({
      seq: event.seq,
      role: 'assistant',
      content: text(payload.content),
      agentName: text(payload.agentName),
    })
  }

  function startRun(event: SessionEvent, payload: Record<string, unknown>) {
    if (!event.runId || runsById.has(event.runId)) {
      return
    }
    const parentRunId = text(payload.parentRunId) || null
    const run = reactive<RunNode>({
      runId: event.runId,
      parentRunId,
      agentName: text(payload.agentName),
      displayName: text(payload.displayName) || text(payload.agentName),
      inputSummary: text(payload.inputSummary),
      status: 'RUNNING',
      outputSummary: '',
      errorMessage: '',
      items: [],
    }) as RunNode
    runsById.set(run.runId, run)

    const parent = parentRunId ? runsById.get(parentRunId) : undefined
    if (parent) {
      // Appending in arrival order puts a delegated specialist directly after the call that asked
      // for it, which is how the trajectory reads as a story rather than as two flat lists.
      parent.items.push({ kind: 'run', run })
    } else {
      rootRuns.value.push(run)
    }
  }

  function finishRun(event: SessionEvent, payload: Record<string, unknown>) {
    const run = event.runId ? runsById.get(event.runId) : undefined
    if (!run) {
      return
    }
    run.status = text(payload.status) || 'COMPLETED'
    run.outputSummary = text(payload.outputSummary)
    run.errorMessage = text(payload.errorMessage)
  }

  function startToolCall(event: SessionEvent, payload: Record<string, unknown>) {
    const run = event.runId ? runsById.get(event.runId) : undefined
    if (!run || !event.callId) {
      return
    }
    run.items.push({
      kind: 'tool',
      tool: {
        callId: event.callId,
        toolName: text(payload.toolName),
        requestSummary: text(payload.requestSummary),
        status: 'RUNNING',
        message: '',
        resultSummary: '',
        durationMs: null,
      },
    })
  }

  function finishToolCall(event: SessionEvent, payload: Record<string, unknown>) {
    const tool = event.callId ? findToolCall(event.callId) : undefined
    if (!tool) {
      return
    }
    tool.status = text(payload.status) || 'SUCCESS'
    tool.message = text(payload.message)
    tool.resultSummary = text(payload.resultSummary)
    tool.durationMs = typeof payload.durationMs === 'number' ? payload.durationMs : null
  }

  function findToolCall(callId: string): ToolCallNode | undefined {
    for (const run of runsById.values()) {
      for (const item of run.items) {
        if (item.kind === 'tool' && item.tool.callId === callId) {
          return item.tool
        }
      }
    }
    return undefined
  }

  function text(value: unknown): string {
    return typeof value === 'string' ? value : ''
  }

  return {
    messages,
    rootRuns,
    lastSeq,
    activeTurnId,
    turnStatus,
    turnRunning,
    runtimeErrors,
    apply,
    reset,
  }
}
