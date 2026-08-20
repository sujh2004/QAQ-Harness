import { createRouter, createWebHistory } from 'vue-router'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/projects' },
    {
      path: '/projects',
      name: 'projects',
      component: () => import('@/views/ProjectsView.vue'),
    },
    {
      path: '/projects/:projectId',
      redirect: (to) => `/projects/${String(to.params.projectId)}/overview`,
    },
    {
      path: '/projects/:projectId/overview',
      name: 'project-overview',
      component: () => import('@/views/OverviewView.vue'),
    },
    {
      path: '/projects/:projectId/logs',
      name: 'project-logs',
      component: () => import('@/views/LogsView.vue'),
    },
    {
      path: '/projects/:projectId/chat',
      name: 'project-chat',
      component: () => import('@/views/ChatView.vue'),
    },
    {
      path: '/projects/:projectId/knowledge',
      name: 'project-knowledge',
      component: () => import('@/views/KnowledgeView.vue'),
    },
    {
      path: '/projects/:projectId/test-cases',
      name: 'project-test-cases',
      component: () => import('@/views/TestCasesView.vue'),
    },
    {
      path: '/projects/:projectId/skills',
      name: 'project-skills',
      component: () => import('@/views/SkillsView.vue'),
    },
    {
      path: '/projects/:projectId/sessions',
      name: 'project-sessions',
      component: () => import('@/views/SessionsView.vue'),
    },
  ],
})
