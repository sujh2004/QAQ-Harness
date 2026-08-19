<script setup lang="ts">
import { ElButton, ElTag } from 'element-plus'
import { computed, onMounted, ref } from 'vue'

import { type ApiResult, http } from '@/api/http'

interface HealthResponse {
  status: string
  application: string
  timestamp: string
}

type HealthState = 'checking' | 'online' | 'offline'

const healthState = ref<HealthState>('checking')
const healthDetails = ref<HealthResponse | null>(null)

const statusLabel = computed(() => {
  if (healthState.value === 'online') return '后端在线'
  if (healthState.value === 'offline') return '后端未连接'
  return '正在检查'
})

const statusType = computed(() => {
  if (healthState.value === 'online') return 'success'
  if (healthState.value === 'offline') return 'danger'
  return 'warning'
})

async function checkHealth() {
  healthState.value = 'checking'
  try {
    const response = await http.get<ApiResult<HealthResponse>>('/api/v1/health')
    healthDetails.value = response.data.data
    healthState.value = response.data.code === 0 ? 'online' : 'offline'
  } catch {
    healthDetails.value = null
    healthState.value = 'offline'
  }
}

onMounted(checkHealth)
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
        <button class="nav-item nav-item--active" type="button">概览</button>
        <button class="nav-item" type="button" disabled>项目</button>
        <button class="nav-item" type="button" disabled>智能对话</button>
        <button class="nav-item" type="button" disabled>知识库</button>
        <button class="nav-item" type="button" disabled>执行审计</button>
      </nav>

      <div class="phase-note">
        <span>当前里程碑</span>
        <strong>Phase 0</strong>
        <p>工程与运行边界已就绪，Agent 能力将在后续阶段启用。</p>
      </div>
    </aside>

    <main class="content">
      <header class="topbar">
        <div>
          <span class="eyebrow">WORKSPACE OVERVIEW</span>
          <h1>让研发判断建立在真实证据上</h1>
        </div>
        <ElTag :type="statusType" effect="plain" round>{{ statusLabel }}</ElTag>
      </header>

      <section class="hero">
        <div class="hero-copy">
          <p class="hero-kicker">MULTI-AGENT · EVENT-SOURCED · AUDITABLE</p>
          <h2>一个可组合、可恢复、可追溯的研发 Agent 工作台。</h2>
          <p>
            DevPilot 将代码、日志、项目知识与测试建议放入受控能力管线，由专业 Agent 协作分析，Supervisor 汇总证据。
          </p>
          <div class="hero-actions">
            <ElButton type="primary" :loading="healthState === 'checking'" @click="checkHealth">
              检查运行状态
            </ElButton>
            <span v-if="healthDetails" class="health-time">{{ healthDetails.timestamp }}</span>
          </div>
        </div>
        <div class="runtime-map" aria-label="运行时能力概览">
          <div class="runtime-node runtime-node--primary">Supervisor</div>
          <div class="runtime-grid">
            <div class="runtime-node">Code</div>
            <div class="runtime-node">Logs</div>
            <div class="runtime-node">Knowledge</div>
            <div class="runtime-node">Tests</div>
          </div>
          <div class="runtime-pipe">Scoped Tool Registry · Session Events</div>
        </div>
      </section>

      <section class="section-heading">
        <div>
          <span class="eyebrow">FOUNDATION</span>
          <h2>本阶段交付</h2>
        </div>
        <span class="section-caption">不以假数据冒充 Agent 能力</span>
      </section>

      <section class="feature-grid">
        <article>
          <span class="feature-index">01</span>
          <h3>模块化后端</h3>
          <p>Java 21、Spring Boot、统一 Result、异常边界与类型化配置。</p>
        </article>
        <article>
          <span class="feature-index">02</span>
          <h3>可验证前端</h3>
          <p>Vue 3、TypeScript、Vite 与 Element Plus 的生产构建基线。</p>
        </article>
        <article>
          <span class="feature-index">03</span>
          <h3>本地基础设施</h3>
          <p>MySQL 开发容器与环境变量示例，密钥和本地配置不入库。</p>
        </article>
        <article>
          <span class="feature-index">04</span>
          <h3>Harness 架构边界</h3>
          <p>能力三角色、追加事件、Tool 管线和 profile 组合已写入实施规范。</p>
        </article>
      </section>
    </main>
  </div>
</template>
