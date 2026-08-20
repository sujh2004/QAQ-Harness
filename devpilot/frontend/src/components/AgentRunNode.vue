<script setup lang="ts">
import { ElTag } from 'element-plus'

import type { RunNode } from '@/composables/useSessionTimeline'

defineProps<{ run: RunNode; depth: number }>()

/** Colour of a run or tool status, so a failure is visible without reading the label. */
function tone(status: string) {
  if (status === 'RUNNING' || status === 'REQUESTED') return 'warning'
  if (status === 'SUCCESS' || status === 'COMPLETED') return 'success'
  if (status === 'CANCELLED') return 'info'
  return 'danger'
}

function duration(ms: number | null) {
  if (ms === null) return ''
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}
</script>

<template>
  <div class="run-node" :style="{ '--depth': depth }">
    <div class="run-head">
      <ElTag :type="tone(run.status)" effect="dark" size="small" round>{{ run.displayName }}</ElTag>
      <code class="run-id">{{ run.agentName }}</code>
      <span v-if="run.status === 'RUNNING'" class="run-pulse">运行中</span>
    </div>

    <p v-if="run.inputSummary" class="run-input">{{ run.inputSummary }}</p>

    <div v-for="(item, index) in run.items" :key="index" class="run-item">
      <div v-if="item.kind === 'tool'" class="tool-call">
        <div class="tool-head">
          <ElTag :type="tone(item.tool.status)" effect="plain" size="small">
            {{ item.tool.toolName }}
          </ElTag>
          <span v-if="item.tool.durationMs !== null" class="tool-duration">
            {{ duration(item.tool.durationMs) }}
          </span>
        </div>
        <p v-if="item.tool.requestSummary" class="tool-line">{{ item.tool.requestSummary }}</p>
        <p v-if="item.tool.resultSummary" class="tool-line tool-line--result">
          {{ item.tool.resultSummary }}
        </p>
        <p v-else-if="item.tool.message" class="tool-line tool-line--error">{{ item.tool.message }}</p>
      </div>

      <AgentRunNode v-else :run="item.run" :depth="depth + 1" />
    </div>

    <p v-if="run.errorMessage" class="run-error">{{ run.errorMessage }}</p>
  </div>
</template>
