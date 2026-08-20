<script setup lang="ts">
import { ElAlert, ElButton, ElEmpty, ElSwitch, ElTag } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  browseSkills,
  installSkill,
  listEnabledSkills,
  listInstalledSkills,
  setSkillEnabled,
  uninstallSkill,
} from '@/api/devpilot'
import type { InstalledSkill, SkillPackage } from '@/api/types'

const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const catalogue = ref<SkillPackage[]>([])
const installed = ref<InstalledSkill[]>([])
const enabledKeys = ref<Set<string>>(new Set())
const expanded = ref<Set<string>>(new Set())
const marketplaceError = ref('')
const error = ref('')
const loading = ref(false)
const busyKey = ref('')

const installedKeys = computed(() => new Set(installed.value.map((skill) => skill.skillKey)))

async function load() {
  loading.value = true
  error.value = ''
  marketplaceError.value = ''
  try {
    const [installedList, enabledList] = await Promise.all([
      listInstalledSkills(),
      listEnabledSkills(projectId.value),
    ])
    installed.value = installedList
    enabledKeys.value = new Set(enabledList.map((skill) => skill.skillKey))
  } catch (cause) {
    error.value = messageOf(cause, '加载已安装技能失败')
  }

  try {
    catalogue.value = await browseSkills()
  } catch (cause) {
    // A marketplace that is not configured is a deployment choice, not a page failure: the rest of
    // the page still shows what is installed.
    catalogue.value = []
    marketplaceError.value = messageOf(cause, '市场不可用')
  } finally {
    loading.value = false
  }
}

async function install(skill: SkillPackage) {
  busyKey.value = skill.key
  error.value = ''
  try {
    await installSkill(skill.key)
    await load()
  } catch (cause) {
    error.value = messageOf(cause, `安装 ${skill.key} 失败`)
  } finally {
    busyKey.value = ''
  }
}

async function remove(skill: InstalledSkill) {
  busyKey.value = skill.skillKey
  try {
    await uninstallSkill(skill.skillKey)
    await load()
  } catch (cause) {
    error.value = messageOf(cause, '卸载失败')
  } finally {
    busyKey.value = ''
  }
}

async function toggle(skill: InstalledSkill, enabled: boolean) {
  busyKey.value = skill.skillKey
  try {
    const result = await setSkillEnabled(projectId.value, skill.skillKey, enabled)
    enabledKeys.value = new Set(result.map((item) => item.skillKey))
  } catch (cause) {
    error.value = messageOf(cause, '切换启用状态失败')
  } finally {
    busyKey.value = ''
  }
}

function toggleSource(key: string) {
  const next = new Set(expanded.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expanded.value = next
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
      <span class="eyebrow">SKILL MARKETPLACE</span>
      <h1>技能市场</h1>
    </div>
    <ElButton :loading="loading" @click="load">刷新</ElButton>
  </header>

  <ElAlert v-if="error" class="page-alert" type="error" :closable="false" :title="error" />

  <section class="panel gates">
    <h3 class="panel-title">下载来的代码要跑起来，需要三个人的决定</h3>
    <ol class="gate-list">
      <li><strong>安装</strong>：把包写到磁盘并记录校验和。清单只能走 HTTPS——能被中途改写的市场就是一条代码执行通道。</li>
      <li><strong>项目启用</strong>：装了不等于能用，还要有人决定这个项目的 Agent 能不能看见它。</li>
      <li><strong>会话审批</strong>：Agent 第一次要执行它时，仍然需要人在这次对话里点头。</li>
    </ol>
    <p class="muted">
      执行发生在沙箱里：解释器白名单、入口路径两次校验、清空环境变量后只注入白名单项（模型密钥与数据库口令都拿不到）、
      参数走 stdin 而不进命令行、临时工作目录、超时即销毁进程树并限制输出大小。
    </p>
  </section>

  <section class="panel">
    <h3 class="panel-title">
      市场
      <span class="muted">浏览不会安装任何东西</span>
    </h3>

    <ElAlert
      v-if="marketplaceError"
      type="warning"
      :closable="false"
      :title="marketplaceError"
      description="配置 SKILL_MARKETPLACE_URL 指向一个 HTTPS 清单后即可浏览。"
    />

    <ElEmpty
      v-else-if="!loading && catalogue.length === 0"
      description="市场里没有可安装的技能"
    />

    <div v-else class="skill-list">
      <article v-for="skill in catalogue" :key="skill.key" class="skill-card">
        <div class="skill-head">
          <strong>{{ skill.name }}</strong>
          <code>{{ skill.key }}</code>
          <ElTag size="small" effect="plain">v{{ skill.version }}</ElTag>
          <ElTag size="small" effect="dark" round>{{ skill.runtime }}</ElTag>
          <span class="case-spacer" />
          <ElButton
            v-if="installedKeys.has(skill.key)"
            disabled
            link
          >
            已安装
          </ElButton>
          <ElButton
            v-else
            type="primary"
            :loading="busyKey === skill.key"
            @click="install(skill)"
          >
            安装
          </ElButton>
        </div>

        <p class="skill-desc">{{ skill.description }}</p>

        <div class="skill-meta muted">
          入口 <code>{{ skill.entrypoint }}</code> ·
          {{ Object.keys(skill.files).length }} 个文件 ·
          <button type="button" class="link-button" @click="toggleSource(skill.key)">
            {{ expanded.has(skill.key) ? '收起源码' : '查看源码（安装前先审阅）' }}
          </button>
        </div>

        <div v-if="expanded.has(skill.key)" class="skill-source">
          <div v-for="(content, path) in skill.files" :key="path">
            <div class="skill-file">{{ path }}</div>
            <pre>{{ content }}</pre>
          </div>
        </div>
      </article>
    </div>
  </section>

  <section class="panel">
    <h3 class="panel-title">
      已安装
      <span class="muted">校验和证明现在跑的就是当初审阅过的内容</span>
    </h3>

    <ElEmpty v-if="!loading && installed.length === 0" description="还没有安装任何技能" />

    <div v-else class="skill-list">
      <article v-for="skill in installed" :key="skill.id" class="skill-card">
        <div class="skill-head">
          <strong>{{ skill.name }}</strong>
          <code>{{ skill.skillKey }}</code>
          <ElTag size="small" effect="plain">v{{ skill.version }}</ElTag>
          <ElTag
            :type="skill.status === 'INSTALLED' ? 'success' : 'info'"
            size="small"
            effect="plain"
          >
            {{ skill.status }}
          </ElTag>
          <span class="case-spacer" />
          <span class="muted">本项目启用</span>
          <ElSwitch
            :model-value="enabledKeys.has(skill.skillKey)"
            :loading="busyKey === skill.skillKey"
            @update:model-value="(value: string | number | boolean) => toggle(skill, Boolean(value))"
          />
          <ElButton link type="danger" :disabled="busyKey === skill.skillKey" @click="remove(skill)">
            卸载
          </ElButton>
        </div>

        <p class="skill-desc">{{ skill.description }}</p>

        <div class="skill-meta muted">
          工具名 <code>{{ skill.skillKey }}</code> ·
          运行时 {{ skill.runtime }} ·
          入口 <code>{{ skill.entrypoint }}</code>
        </div>
        <div class="skill-meta muted">
          来源 {{ skill.sourceUrl || '—' }}
        </div>
        <div class="skill-meta muted">
          sha256 <code>{{ skill.checksum }}</code>
        </div>
      </article>
    </div>
  </section>
</template>
