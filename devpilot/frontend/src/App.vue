<script setup lang="ts">
import { ElTag } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

import { type ApiResult, http } from '@/api/http'

interface HealthResponse {
  status: string
  application: string
}

type HealthState = 'checking' | 'online' | 'offline'

const route = useRoute()
const healthState = ref<HealthState>('checking')

const projectId = computed(() => {
  const value = route.params.projectId
  return typeof value === 'string' ? value : ''
})

const statusLabel = computed(() => {
  if (healthState.value === 'online') return '后端在线'
  if (healthState.value === 'offline') return '后端未连接'
  return '检查中'
})

const statusType = computed(() => {
  if (healthState.value === 'online') return 'success'
  if (healthState.value === 'offline') return 'danger'
  return 'warning'
})

async function checkHealth() {
  try {
    const response = await http.get<ApiResult<HealthResponse>>('/api/v1/health')
    healthState.value = response.data.code === 0 ? 'online' : 'offline'
  } catch {
    healthState.value = 'offline'
  }
}

onMounted(() => {
  void checkHealth()
  window.setInterval(() => void checkHealth(), 15000)
})
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">DP</div>
        <div>
          <strong>DevPilot</strong>
          <span>研发智能协作平台</span>
        </div>
      </div>

      <nav aria-label="主导航">
        <RouterLink class="nav-item" active-class="nav-item--active" to="/projects">项目</RouterLink>
        <template v-if="projectId">
          <RouterLink
            class="nav-item"
            active-class="nav-item--active"
            :to="`/projects/${projectId}/overview`"
          >
            项目概览
          </RouterLink>
          <RouterLink
            class="nav-item"
            active-class="nav-item--active"
            :to="`/projects/${projectId}/chat`"
          >
            智能对话
          </RouterLink>
          <RouterLink
            class="nav-item"
            active-class="nav-item--active"
            :to="`/projects/${projectId}/knowledge`"
          >
            知识库
          </RouterLink>
          <RouterLink
            class="nav-item"
            active-class="nav-item--active"
            :to="`/projects/${projectId}/logs`"
          >
            日志
          </RouterLink>
          <RouterLink
            class="nav-item"
            active-class="nav-item--active"
            :to="`/projects/${projectId}/test-cases`"
          >
            测试用例
          </RouterLink>
          <RouterLink
            class="nav-item"
            active-class="nav-item--active"
            :to="`/projects/${projectId}/skills`"
          >
            技能市场
          </RouterLink>
          <RouterLink
            class="nav-item"
            active-class="nav-item--active"
            :to="`/projects/${projectId}/sessions`"
          >
            会话与轨迹
          </RouterLink>
        </template>
      </nav>

      <div class="phase-note">
        <span>当前里程碑</span>
        <strong>Phase 9</strong>
        <p>两套故障剧情、八个页面与一键启动，MVP 闭环完成。</p>
        <ElTag :type="statusType" effect="dark" size="small" round>{{ statusLabel }}</ElTag>
      </div>
    </aside>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>
