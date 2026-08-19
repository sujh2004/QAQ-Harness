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
      path: '/projects/:projectId/sessions',
      name: 'project-sessions',
      component: () => import('@/views/SessionsView.vue'),
    },
  ],
})
