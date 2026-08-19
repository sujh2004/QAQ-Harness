<script setup lang="ts">
import { ElAlert, ElButton, ElDescriptions, ElDescriptionsItem, ElEmpty, ElTag } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { errorSummary, getProject, validateRepository } from '@/api/devpilot'
import type { ErrorSummary, Project, RepositoryValidation } from '@/api/types'

const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const project = ref<Project | null>(null)
const repository = ref<RepositoryValidation | null>(null)
const errors = ref<ErrorSummary[]>([])
const loading = ref(true)
const error = ref('')

/** The demo incidents are historical, so the summary window reaches back far enough to show them. */
const SUMMARY_HOURS = 720

async function load() {
  loading.value = true
  error.value = ''
  try {
    project.value = await getProject(projectId.value)
    const [validation, summary] = await Promise.all([
      validateRepository(projectId.value),
      errorSummary(projectId.value, SUMMARY_HOURS),
    ])
    repository.value = validation
    errors.value = summary
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(projectId, load)
</script>

<template>
  <header class="topbar">
    <div>
      <span class="eyebrow">OVERVIEW</span>
      <h1>{{ project?.name ?? '项目概览' }}</h1>
    </div>
    <ElButton :loading="loading" @click="load">刷新</ElButton>
  </header>

  <ElAlert v-if="error" class="page-alert" type="error" :closable="false" :title="error" />

  <template v-else>
    <section class="panel">
      <h3 class="panel-title">基本信息</h3>
      <ElDescriptions :column="2" border>
        <ElDescriptionsItem label="项目编码">{{ project?.code }}</ElDescriptionsItem>
        <ElDescriptionsItem label="默认分支">{{ project?.defaultBranch }}</ElDescriptionsItem>
        <ElDescriptionsItem label="描述" :span="2">
          {{ project?.description || '—' }}
        </ElDescriptionsItem>
      </ElDescriptions>
    </section>

    <section class="panel">
      <h3 class="panel-title">
        代码仓库
        <ElTag
          v-if="repository"
          :type="repository.accessible ? 'success' : 'danger'"
          effect="plain"
          size="small"
        >
          {{ repository.accessible ? '可读取' : '不可读取' }}
        </ElTag>
      </h3>
      <ElDescriptions v-if="repository" :column="1" border>
        <ElDescriptionsItem label="配置路径">
          <code>{{ repository.repositoryPath }}</code>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="解析后的绝对路径">
          <code>{{ repository.resolvedPath }}</code>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="检查结果">
          <span class="check" :class="{ 'check--ok': repository.exists }">存在</span>
          <span class="check" :class="{ 'check--ok': repository.directory }">是目录</span>
          <span class="check" :class="{ 'check--ok': repository.readable }">可读</span>
          <span class="check" :class="{ 'check--ok': repository.gitRepository }">Git 仓库</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="说明">{{ repository.detail }}</ElDescriptionsItem>
      </ElDescriptions>
    </section>

    <section class="panel">
      <h3 class="panel-title">
        近期错误聚合
        <span class="muted">最近 {{ SUMMARY_HOURS }} 小时</span>
      </h3>
      <ElEmpty v-if="!loading && errors.length === 0" description="窗口内没有 ERROR 日志" />
      <div v-else class="incident-list">
        <article v-for="group in errors" :key="`${group.serviceName}-${group.exceptionType}`">
          <div class="incident-head">
            <strong>{{ group.exceptionType ?? '未分类错误' }}</strong>
            <ElTag type="danger" effect="dark" size="small" round>×{{ group.occurrences }}</ElTag>
          </div>
          <div class="muted">{{ group.serviceName }} · {{ group.firstSeen }} → {{ group.lastSeen }}</div>
          <p class="incident-sample">{{ group.sampleMessage }}</p>
        </article>
      </div>
    </section>
  </template>
</template>
