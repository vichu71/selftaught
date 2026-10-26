import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { createAppRouter } from './router'
import './styles/tokens.css'
import './styles/fonts.css'
import './styles/base.css'
import './styles/components.css'
import './styles/motion.css'

createApp(App).use(createPinia()).use(createAppRouter()).mount('#app')
