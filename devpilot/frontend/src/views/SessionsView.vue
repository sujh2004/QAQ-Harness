<script setup lang="ts">
import { ElAlert, ElButton, ElEmpty, ElTag } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { createSession, listEvents, listMessages, listRuns, listSessions } from '@/api/devpilot'
import type { AgentRun, Message, Session, SessionEvent } from '@/api/types'

const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const sessions = ref<Session[]>([])
const selected = ref<Session | null>(null)
const events = ref<SessionEvent[]>([])
const messages = ref<Message[]>([])
const runs = ref<AgentRun[]>([])
const loading = ref(false)
const error = ref('')

/** Events that open something, so the timeline can mark lifecycle boundaries. */
const OPENING = new Set(['session_created', 'turn_started', 'step_started', 'agent_started', 'tool_call_requested'])
const CLOSING = new Set(['turn_ended', 'step_ended', 'agent_finished', 'tool_call_finished'])

async function loadSessions() {
  loading.value = true
  error.value = ''
  try {
    sessions.value = (await listSessions(projectId.value)).items
    const first = sessions.value[0]
    if (first) {
      await select(first)
    } else {
      selected.value = null
      events.value = []
      messages.value = []
      runs.value = []
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function select(session: Session) {
  selected.value = session
  const [eventList, messageList, runList] = await Promise.all([
    listEvents(session.sessionId),
    listMessages(session.sessionId),
    listRuns(session.sessionId),
  ])
  events.value = eventList
  messages.value = messageList.items
  runs.value = runList
}

async function newSession() {
  loading.value = true
  try {
    const created = await createSession(projectId.value, `站会演示 ${new Date().toLocaleTimeString()}`)
    sessions.value = [created, ...sessions.value]
    await select(created)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '创建失败'
  } finally {
    loading.value = false
  }
}

function eventClass(type: string) {
  if (OPENING.has(type)) return 'event--open'
  if (CLOSING.has(type)) return 'event--close'
  if (type === 'runtime_error') return 'event--error'
  return ''
}

function payloadOf(event: SessionEvent) {
  return JSON.stringify(event.payload, null, 2)
}

onMounted(loadSessions)
watch(projectId, loadSessions)
</script>

<template>
  <header class="topbar">
    <div>
      <span class="eyebrow">SESSIONS &amp; EVENT LOG</span>
      <h1>会话与轨迹</h1>
    </div>
    <div class="topbar-actions">
      <ElButton :loading="loading" @click="loadSessions">刷新</ElButton>
      <ElButton type="primary" :loading="loading" @click="newSession">新建会话</ElButton>
    </div>
  </header>

  <ElAlert v-if="error" class="page-alert" type="error" :closable="false" :title="error" />

  <ElEmpty v-else-if="!loading && sessions.length === 0" description="还没有会话，点击“新建会话”开始" />

  <div v-else class="session-layout">
    <aside class="session-list panel">
      <h3 class="panel-title">会话</h3>
      <button
        v-for="session in sessions"
        :key="session.sessionId"
        type="button"
        class="session-item"
        :class="{ 'session-item--active': selected?.sessionId === session.sessionId }"
        @click="select(session)"
      >
        <strong>{{ session.title }}</strong>
        <span>{{ session.createdAt.replace('T', ' ') }}</span>
      </button>
    </aside>

    <section class="panel">
      <h3 class="panel-title">
        事件流
        <span class="muted">session_event 是运行事实的唯一来源，下面全部由它投影而来</span>
      </h3>

      <div class="stat-row">
        <div><span>事件</span><strong>{{ events.length }}</strong></div>
        <div><span>消息</span><strong>{{ messages.length }}</strong></div>
        <div><span>Agent 运行</span><strong>{{ runs.length }}</strong></div>
      </div>

      <ol class="timeline">
        <li v-for="event in events" :key="event.seq" :class="eventClass(event.eventType)">
          <span class="timeline-seq">#{{ event.seq }}</span>
          <div class="timeline-body">
            <div class="timeline-head">
              <ElTag size="small" effect="plain">{{ event.eventType }}</ElTag>
              <time>{{ event.occurredAt.replace('T', ' ').replace('Z', '') }}</time>
              <code v-if="event.turnId" class="timeline-id">{{ event.turnId.slice(0, 13) }}</code>
            </div>
            <pre class="timeline-payload">{{ payloadOf(event) }}</pre>
          </div>
        </li>
      </ol>
    </section>
  </div>
</template>
