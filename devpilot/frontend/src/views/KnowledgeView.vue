<script setup lang="ts">
import { ElAlert, ElButton, ElEmpty, ElInput, ElTag } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  deleteKnowledge,
  listKnowledge,
  reindexKnowledge,
  searchKnowledge,
  uploadKnowledge,
} from '@/api/devpilot'
import type { KnowledgeDocument, KnowledgeMatch } from '@/api/types'

const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const documents = ref<KnowledgeDocument[]>([])
const matches = ref<KnowledgeMatch[]>([])
const query = ref('')
const searched = ref('')
const uploadName = ref('')
const uploadType = ref('')
const uploadContent = ref('')
const showUpload = ref(false)
const loading = ref(false)
const busy = ref(false)
const error = ref('')

const totalChunks = computed(() => documents.value.reduce((sum, doc) => sum + doc.chunkCount, 0))

async function load() {
  loading.value = true
  error.value = ''
  try {
    documents.value = await listKnowledge(projectId.value)
  } catch (cause) {
    error.value = messageOf(cause, '加载知识库失败')
  } finally {
    loading.value = false
  }
}

async function search() {
  const text = query.value.trim()
  if (!text) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    matches.value = await searchKnowledge(projectId.value, text, 5)
    searched.value = text
  } catch (cause) {
    error.value = messageOf(cause, '检索失败')
  } finally {
    busy.value = false
  }
}

async function upload() {
  const name = uploadName.value.trim()
  const content = uploadContent.value.trim()
  if (!name || !content) {
    error.value = '文档名与内容都不能为空'
    return
  }
  busy.value = true
  error.value = ''
  try {
    await uploadKnowledge(projectId.value, {
      documentName: name,
      documentType: uploadType.value.trim() || undefined,
      content,
    })
    uploadName.value = ''
    uploadType.value = ''
    uploadContent.value = ''
    showUpload.value = false
    await load()
  } catch (cause) {
    error.value = messageOf(cause, '导入失败')
  } finally {
    busy.value = false
  }
}

async function reindex() {
  busy.value = true
  error.value = ''
  try {
    documents.value = await reindexKnowledge(projectId.value)
  } catch (cause) {
    error.value = messageOf(cause, '重建索引失败')
  } finally {
    busy.value = false
  }
}

async function remove(document: KnowledgeDocument) {
  busy.value = true
  error.value = ''
  try {
    await deleteKnowledge(projectId.value, document.id)
    // Deletion rebuilds the index, so anything retrieved earlier may now be stale.
    matches.value = []
    await load()
  } catch (cause) {
    error.value = messageOf(cause, '删除失败')
  } finally {
    busy.value = false
  }
}

/** Colour of a relevance score, so a weak match does not look like a strong one. */
function scoreTone(score: number) {
  if (score >= 0.75) return 'success'
  if (score >= 0.65) return 'warning'
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
      <span class="eyebrow">KNOWLEDGE BASE</span>
      <h1>项目知识库</h1>
    </div>
    <div class="topbar-actions">
      <ElButton :loading="busy" @click="reindex">重建索引</ElButton>
      <ElButton type="primary" @click="showUpload = !showUpload">
        {{ showUpload ? '收起' : '导入文档' }}
      </ElButton>
    </div>
  </header>

  <ElAlert v-if="error" class="page-alert" type="error" :closable="false" :title="error" />

  <section v-if="showUpload" class="panel">
    <h3 class="panel-title">导入文档</h3>
    <div class="upload-form">
      <ElInput v-model="uploadName" placeholder="文档名，例如 错误码规范.md" />
      <ElInput v-model="uploadType" placeholder="类型，例如 standards / incident-review" />
      <ElInput
        v-model="uploadContent"
        type="textarea"
        :rows="8"
        resize="none"
        placeholder="Markdown 正文。按标题切块，标题会与它引出的正文留在同一块里。"
      />
      <ElButton type="primary" :loading="busy" @click="upload">导入并建立索引</ElButton>
    </div>
  </section>

  <section class="panel">
    <h3 class="panel-title">
      检索
      <span class="muted">每段都标明出处与相关度；低于阈值按「未找到」处理</span>
    </h3>
    <div class="search-row">
      <ElInput
        v-model="query"
        placeholder="例如：优惠券服务返回 null 应该怎么处理"
        @keyup.enter="search"
      />
      <ElButton type="primary" :loading="busy" @click="search">检索</ElButton>
    </div>

    <ElEmpty
      v-if="searched && matches.length === 0"
      :description="`知识库中没有与「${searched}」相关度足够高的内容`"
    />
    <ol v-else-if="matches.length" class="match-list">
      <li v-for="(match, index) in matches" :key="index">
        <div class="match-head">
          <strong>{{ match.documentName }}</strong>
          <ElTag v-if="match.documentType" size="small" effect="plain">{{ match.documentType }}</ElTag>
          <ElTag :type="scoreTone(match.score)" size="small" effect="dark" round>
            {{ match.score.toFixed(3) }}
          </ElTag>
        </div>
        <pre class="match-chunk">{{ match.chunk }}</pre>
      </li>
    </ol>
  </section>

  <section class="panel">
    <h3 class="panel-title">
      已导入文档
      <span class="muted">{{ documents.length }} 份 · {{ totalChunks }} 段</span>
    </h3>
    <ElEmpty v-if="!loading && documents.length === 0" description="还没有导入任何文档" />
    <table v-else class="doc-table">
      <thead>
        <tr>
          <th>文档</th>
          <th>类型</th>
          <th>分段</th>
          <th>状态</th>
          <th>导入时间</th>
          <th />
        </tr>
      </thead>
      <tbody>
        <tr v-for="document in documents" :key="document.id">
          <td>
            <strong>{{ document.documentName }}</strong>
            <div v-if="document.sourcePath" class="muted">{{ document.sourcePath }}</div>
          </td>
          <td>{{ document.documentType || '—' }}</td>
          <td>{{ document.chunkCount }}</td>
          <td>
            <ElTag
              :type="document.vectorStatus === 'INDEXED' ? 'success' : 'danger'"
              size="small"
              effect="plain"
            >
              {{ document.vectorStatus }}
            </ElTag>
          </td>
          <td>{{ document.createdAt.replace('T', ' ').slice(0, 19) }}</td>
          <td>
            <ElButton link type="danger" :disabled="busy" @click="remove(document)">删除</ElButton>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
