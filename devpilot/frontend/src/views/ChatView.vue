<script setup lang="ts">
import { ElAlert, ElButton, ElInput, ElOption, ElSelect, ElTag } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { streamChat } from '@/api/chatStream'
import { cancelTurn, createSession, listAllEvents, listSessions } from '@/api/devpilot'
import type { ChatStreamRequest, Session, SessionEvent } from '@/api/types'
import AgentRunNode from '@/components/AgentRunNode.vue'
import { useSessionTimeline } from '@/composables/useSessionTimeline'

const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const { messages, rootRuns, lastSeq, activeTurnId, turnStatus, turnRunning, runtimeErrors, apply, reset } =
  useSessionTimeline()

const sessions = ref<Session[]>([])
const sessionId = ref('')
const draft = ref('')
const error = ref('')
const notice = ref('')
const streaming = ref(false)
const threadRef = ref<HTMLElement | null>(null)

/** How many times a dropped stream is resumed before the user is told it failed. */
const MAX_RECONNECTS = 3

let controller: AbortController | null = null

const canSend = computed(() => draft.value.trim().length > 0 && !streaming.value)

async function loadSessions() {
  error.value = ''
  try {
    sessions.value = (await listSessions(projectId.value)).items
    // Another page may have linked to a specific conversation, for example the test case that was
    // designed in it; honouring that beats always opening the newest one.
    const requested = typeof route.query.session === 'string' ? route.query.session : ''
    const target = sessions.value.find((session) => session.sessionId === requested)
      ?? sessions.value[0]
    if (target) {
      await selectSession(target.sessionId)
    } else {
      sessionId.value = ''
      reset()
    }
  } catch (cause) {
    error.value = messageOf(cause, '加载会话失败')
  }
}

/**
 * Switches to a session and rebuilds its timeline from the event log.
 *
 * <p>The page never restores a cached view: replaying the events is what guarantees a refresh shows
 * the same thing the live stream showed.
 *
 * @param id session to open
 */
async function selectSession(id: string) {
  stopStream()
  sessionId.value = id
  reset()
  try {
    const events = await listAllEvents(id)
    events.forEach(apply)
    await scrollToEnd()
    if (turnRunning.value) {
      // A turn is still running — started in another tab, or by this one before it reloaded.
      void follow()
    }
  } catch (cause) {
    error.value = messageOf(cause, '加载会话事件失败')
  }
}

async function newSession() {
  try {
    const created = await createSession(projectId.value, `对话 ${new Date().toLocaleTimeString()}`)
    sessions.value = [created, ...sessions.value]
    await selectSession(created.sessionId)
  } catch (cause) {
    error.value = messageOf(cause, '创建会话失败')
  }
}

async function send() {
  const question = draft.value.trim()
  if (!question || streaming.value) {
    return
  }
  draft.value = ''
  error.value = ''
  await run({ projectId: projectId.value, sessionId: sessionId.value || undefined, message: question })
}

/** Reattaches to a turn that is already running, without asking anything again. */
async function follow() {
  await run({ projectId: projectId.value, sessionId: sessionId.value })
}

/**
 * Opens a stream and keeps it open until the turn ends.
 *
 * <p>A dropped connection is not a lost answer: the turn keeps running on the server, and the
 * client reattaches with the sequence number it already holds. The question is deliberately not
 * resent — that would start a second turn asking the same thing.
 *
 * @param request first attempt; retries always attach instead of asking
 */
async function run(request: ChatStreamRequest) {
  streaming.value = true
  controller = new AbortController()
  const signal = controller.signal
  let attempt = 0

  try {
    let next = request
    for (;;) {
      try {
        await streamChat(next, lastSeq.value || null, onEvent, signal)
      } catch (cause) {
        if (signal.aborted) {
          return
        }
        if (!sessionId.value || lastSeq.value === 0 || attempt >= MAX_RECONNECTS) {
          throw cause
        }
        attempt += 1
        notice.value = `连接中断，正在从事件 #${lastSeq.value} 续传（第 ${attempt} 次）`
        await delay(500 * attempt)
        next = { projectId: projectId.value, sessionId: sessionId.value }
        continue
      }

      if (signal.aborted || !turnRunning.value) {
        return
      }
      // The server closed a healthy stream while the turn is still running — a stream timeout, or a
      // client that fell behind. Reattaching replays exactly the gap; the pause keeps a server that
      // closes streams immediately from turning this into a hot loop.
      attempt = 0
      notice.value = '流已重置，正在续传'
      await delay(300)
      if (signal.aborted) {
        return
      }
      next = { projectId: projectId.value, sessionId: sessionId.value }
    }
  } catch (cause) {
    error.value = messageOf(cause, '对话失败')
  } finally {
    streaming.value = false
    controller = null
    notice.value = ''
    await refreshSessionsIfNew()
  }
}

function onEvent(event: SessionEvent) {
  if (!sessionId.value) {
    sessionId.value = event.sessionId
  }
  apply(event)
  void scrollToEnd()
}

async function refreshSessionsIfNew() {
  if (sessionId.value && !sessions.value.some((session) => session.sessionId === sessionId.value)) {
    try {
      sessions.value = (await listSessions(projectId.value)).items
    } catch {
      // The thread is already on screen; a stale session list is not worth an error banner.
    }
  }
}

async function cancel() {
  const turnId = activeTurnId.value
  if (!sessionId.value || !turnId) {
    return
  }
  try {
    await cancelTurn(sessionId.value, turnId)
  } catch (cause) {
    error.value = messageOf(cause, '取消失败')
  }
}

function stopStream() {
  controller?.abort()
  controller = null
  streaming.value = false
}

async function scrollToEnd() {
  await nextTick()
  const element = threadRef.value
  if (element) {
    element.scrollTop = element.scrollHeight
  }
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function messageOf(cause: unknown, fallback: string) {
  return cause instanceof Error ? cause.message : fallback
}

onMounted(loadSessions)
onUnmounted(stopStream)
watch(projectId, loadSessions)
</script>

<template>
  <header class="topbar">
    <div>
      <span class="eyebrow">CHAT</span>
      <h1>智能对话</h1>
    </div>
    <div class="topbar-actions">
      <ElSelect
        v-if="sessions.length"
        :model-value="sessionId"
        class="session-select"
        placeholder="选择会话"
        @update:model-value="selectSession"
      >
        <ElOption
          v-for="session in sessions"
          :key="session.sessionId"
          :label="session.title"
          :value="session.sessionId"
        />
      </ElSelect>
      <ElButton @click="newSession">新建会话</ElButton>
    </div>
  </header>

  <ElAlert v-if="error" class="page-alert" type="error" :closable="false" :title="error" />
  <ElAlert v-if="notice" class="page-alert" type="warning" :closable="false" :title="notice" />

  <div class="chat-layout">
    <section class="panel chat-panel">
      <div ref="threadRef" class="chat-thread">
        <p v-if="messages.length === 0" class="chat-hint">
          问一个真实的问题，例如「order-service 最近为什么报错？」——Supervisor 会决定该问哪个专业
          Agent，右侧实时显示它的调度轨迹。
        </p>

        <article
          v-for="entry in messages"
          :key="entry.seq"
          class="bubble"
          :class="entry.role === 'user' ? 'bubble--user' : 'bubble--assistant'"
        >
          <div class="bubble-role">{{ entry.role === 'user' ? '我' : entry.agentName || 'DevPilot' }}</div>
          <div class="bubble-body">{{ entry.content }}</div>
        </article>

        <div v-if="streaming" class="chat-status">
          <span class="dot" /><span class="dot" /><span class="dot" />
          <span>{{ turnRunning ? 'Agent 正在工作' : '连接中' }}</span>
        </div>
      </div>

      <div class="chat-composer">
        <ElInput
          v-model="draft"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="描述你的问题，Ctrl + Enter 发送"
          @keydown.ctrl.enter.prevent="send"
        />
        <div class="composer-actions">
          <ElButton v-if="turnRunning" type="danger" plain @click="cancel">取消本轮</ElButton>
          <ElButton type="primary" :disabled="!canSend" :loading="streaming" @click="send">
            发送
          </ElButton>
        </div>
      </div>
    </section>

    <aside class="panel trace-panel">
      <h3 class="panel-title">
        Agent 轨迹
        <ElTag v-if="turnStatus" size="small" effect="plain">{{ turnStatus }}</ElTag>
      </h3>
      <p class="muted trace-note">全部来自 session_event，刷新页面后由事件回放重建</p>

      <p v-if="rootRuns.length === 0" class="chat-hint">还没有 Agent 运行</p>
      <AgentRunNode v-for="run in rootRuns" :key="run.runId" :run="run" :depth="0" />

      <ElAlert
        v-for="(runtimeError, index) in runtimeErrors"
        :key="index"
        class="page-alert"
        type="error"
        :closable="false"
        :title="runtimeError"
      />
    </aside>
  </div>
</template>
