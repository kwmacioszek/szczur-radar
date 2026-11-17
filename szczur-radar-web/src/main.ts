import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import { createAppRouter } from './router'
import { initTheme } from './theme'

initTheme()

createApp(App).use(createAppRouter()).mount('#app')
