<script setup lang="ts">
import { ElAlert, ElButton, ElEmpty, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listProjects } from '@/api/devpilot'
import type { Project } from '@/api/types'

const router = useRouter()
const projects = ref<Project[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    projects.value = (await listProjects()).items
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function open(project: Project) {
  void router.push(`/projects/${project.id}/overview`)
}

/** Element Plus hands table rows back untyped, so the row is narrowed here. */
function openRow(row: unknown) {
  open(row as Project)
}

onMounted(load)
</script>

<template>
  <header class="topbar">
    <div>
      <span class="eyebrow">PROJECTS</span>
      <h1>项目</h1>
    </div>
    <ElButton :loading="loading" @click="load">刷新</ElButton>
  </header>

  <ElAlert
    v-if="error"
    class="page-alert"
    type="error"
    :closable="false"
    title="无法读取项目列表"
    :description="`${error}。请确认后端已启动，并已执行 sql/schema.sql 与 sql/demo-data.sql。`"
  />

  <ElEmpty v-else-if="!loading && projects.length === 0" description="还没有项目" />

  <div v-else class="panel">
    <ElTable :data="projects" v-loading="loading" style="width: 100%">
      <ElTableColumn prop="name" label="项目" min-width="180">
        <template #default="{ row }">
          <strong>{{ row.name }}</strong>
          <div class="muted">{{ row.code }}</div>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="description" label="描述" min-width="240" show-overflow-tooltip />
      <ElTableColumn prop="repositoryPath" label="仓库路径" min-width="220">
        <template #default="{ row }">
          <code>{{ row.repositoryPath }}</code>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="defaultBranch" label="分支" width="110" />
      <ElTableColumn label="状态" width="100">
        <template #default="{ row }">
          <ElTag :type="row.status === 1 ? 'success' : 'info'" effect="plain" size="small">
            {{ row.status === 1 ? '启用' : '归档' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="" width="120" align="right">
        <template #default="{ row }">
          <ElButton type="primary" link @click="openRow(row)">进入项目 →</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
