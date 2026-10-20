import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles.css'
import './styles/theme-light.css'
import './styles/theme-dark.css'
import './styles/theme-classic.css'

createApp(App).use(createPinia()).use(router).mount('#app')
