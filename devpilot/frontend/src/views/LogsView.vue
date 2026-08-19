<script setup lang="ts">
import {
  ElAlert,
  ElButton,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
  ElTag,
} from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { searchLogs } from '@/api/devpilot'
import type { LogEntry } from '@/api/types'

const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const filters = reactive({
  serviceName: '',
  level: '',
  keyword: '',
  traceId: '',
})

const entries = ref<LogEntry[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const loading = ref(false)
const error = ref('')
const expanded = ref<Set<number>>(new Set())

const LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL']

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await searchLogs(projectId.value, {
      serviceName: filters.serviceName,
      level: filters.level,
      keyword: filters.keyword,
      traceId: filters.traceId,
      page: page.value,
      size: size.value,
    })
    entries.value = result.items
    total.value = result.total
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 0
  void load()
}

function reset() {
  filters.serviceName = ''
  filters.level = ''
  filters.keyword = ''
  filters.traceId = ''
  applyFilters()
}

function toggle(id: number) {
  const next = new Set(expanded.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expanded.value = next
}

function levelType(level: string) {
  if (level === 'ERROR' || level === 'FATAL') return 'danger'
  if (level === 'WARN') return 'warning'
  if (level === 'INFO') return 'success'
  return 'info'
}

function onPageChange(next: number) {
  page.value = next - 1
  void load()
}

onMounted(load)
watch(projectId, applyFilters)
</script>

<template>
  <header class="topbar">
    <div>
      <span class="eyebrow">SYSTEM LOGS</span>
      <h1>日志</h1>
    </div>
    <ElButton :loading="loading" @click="load">刷新</ElButton>
  </header>

  <section class="panel filter-bar">
    <ElInput v-model="filters.serviceName" placeholder="服务名" clearable @keyup.enter="applyFilters" />
    <ElSelect v-model="filters.level" placeholder="级别" clearable style="width: 130px">
      <ElOption v-for="level in LEVELS" :key="level" :label="level" :value="level" />
    </ElSelect>
    <ElInput v-model="filters.keyword" placeholder="关键词（消息或异常类型）" clearable @keyup.enter="applyFilters" />
    <ElInput v-model="filters.traceId" placeholder="traceId" clearable @keyup.enter="applyFilters" />
    <ElButton type="primary" :loading="loading" @click="applyFilters">查询</ElButton>
    <ElButton @click="reset">重置</ElButton>
  </section>

  <ElAlert v-if="error" class="page-alert" type="error" :closable="false" :title="error" />

  <ElEmpty v-else-if="!loading && entries.length === 0" description="没有匹配的日志" />

  <template v-else>
    <div class="log-list" v-loading="loading">
      <article
        v-for="entry in entries"
        :key="entry.id"
        class="log-row"
        :class="{ 'log-row--error': entry.level === 'ERROR' || entry.level === 'FATAL' }"
      >
        <div class="log-head" @click="toggle(entry.id)">
          <ElTag :type="levelType(entry.level)" effect="dark" size="small">{{ entry.level }}</ElTag>
          <time>{{ entry.logTime.replace('T', ' ') }}</time>
          <span class="log-service">{{ entry.serviceName }}</span>
          <span v-if="entry.traceId" class="log-trace">{{ entry.traceId }}</span>
          <span class="log-message">{{ entry.message }}</span>
          <span v-if="entry.stackTrace" class="log-toggle">
            {{ expanded.has(entry.id) ? '收起' : '堆栈' }}
          </span>
        </div>
        <div v-if="entry.logger || entry.exceptionType" class="log-meta">
          <span v-if="entry.logger">{{ entry.logger }}</span>
          <span v-if="entry.exceptionType" class="log-exception">{{ entry.exceptionType }}</span>
        </div>
        <pre v-if="expanded.has(entry.id) && entry.stackTrace" class="log-stack">{{ entry.stackTrace }}</pre>
      </article>
    </div>

    <ElPagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page + 1"
      @current-change="onPageChange"
    />
  </template>
</template>
