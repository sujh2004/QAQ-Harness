import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-tag.css'
import './styles.css'

import { createApp } from 'vue'

import App from './App.vue'
import router from './router'

createApp(App).use(router).mount('#app')
