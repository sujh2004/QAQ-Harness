<script setup lang="ts">
import { ElAlert, ElButton, ElEmpty, ElTag } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { deleteTestCase, listTestCases } from '@/api/devpilot'
import type { TestCase } from '@/api/types'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.projectId))

const cases = ref<TestCase[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')

const byAgent = computed(() => cases.value.filter((item) => item.source === 'AGENT').length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    cases.value = (await listTestCases(projectId.value)).items
  } catch (cause) {
    error.value = messageOf(cause, '加载测试用例失败')
  } finally {
    loading.value = false
  }
}

async function remove(testCase: TestCase) {
  busy.value = true
  try {
    await deleteTestCase(testCase.id)
    await load()
  } catch (cause) {
    error.value = messageOf(cause, '删除失败')
  } finally {
    busy.value = false
  }
}

/** Opens the conversation the case was designed in, so a reviewer can read the evidence. */
function openSession(testCase: TestCase) {
  if (!testCase.sessionId) {
    return
  }
  void router.push(`/projects/${projectId.value}/chat?session=${testCase.sessionId}`)
}

/** Colour by severity: a P0 regression case should not look like a P3 one. */
function priorityTone(priority: string | null) {
  if (priority === 'P0') return 'danger'
  if (priority === 'P1') return 'warning'
  if (priority === 'P2') return 'success'
  return 'info'
}

function messageOf(cause: unknown, fallback: string) {
  return cause instanceof Error ? cause.message : fallback
}

onMounted(load)
watch(projectId, load)
</script>

<template>
  <header class="topbar">
    <div>
      <span class="eyebrow">TEST CASES</span>
      <h1>测试用例</h1>
    </div>
    <ElButton :loading="loading" @click="load">刷新</ElButton>
  </header>

  <ElAlert v-if="error" class="page-alert" type="error" :closable="false" :title="error" />

  <section class="panel">
    <h3 class="panel-title">
      已落库用例
      <span class="muted">
        共 {{ cases.length }} 条，其中 {{ byAgent }} 条由测试 Agent 生成 ·
        saveTestCases 是全系统唯一的写工具
      </span>
    </h3>

    <ElEmpty
      v-if="!loading && cases.length === 0"
      description="还没有用例。在对话页让测试 Agent 设计回归方案，结果会自动出现在这里"
    />

    <div v-else class="case-list">
      <article v-for="testCase in cases" :key="testCase.id" class="case-card">
        <div class="case-head">
          <ElTag :type="priorityTone(testCase.priority)" effect="dark" size="small" round>
            {{ testCase.priority || 'P?' }}
          </ElTag>
          <strong>{{ testCase.title }}</strong>
          <ElTag size="small" effect="plain">{{ testCase.source }}</ElTag>
          <span class="case-spacer" />
          <ElButton v-if="testCase.sessionId" link @click="openSession(testCase)">来源会话</ElButton>
          <ElButton link type="danger" :disabled="busy" @click="remove(testCase)">删除</ElButton>
        </div>

        <div v-if="testCase.precondition" class="case-row">
          <span class="case-label">前置</span>
          <p>{{ testCase.precondition }}</p>
        </div>

        <div class="case-row">
          <span class="case-label">步骤</span>
          <ol class="case-steps">
            <li v-for="(step, index) in testCase.steps" :key="index">{{ step }}</li>
          </ol>
        </div>

        <div class="case-row">
          <span class="case-label">预期</span>
          <p>{{ testCase.expectedResult }}</p>
        </div>

        <div class="case-foot muted">
          #{{ testCase.id }} · {{ testCase.createdAt.replace('T', ' ').slice(0, 19) }}
          <template v-if="testCase.sessionId"> · {{ testCase.sessionId }}</template>
        </div>
      </article>
    </div>
  </section>
</template>
