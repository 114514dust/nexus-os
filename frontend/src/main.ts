import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { registerBuiltinWidgets } from './widgets/registry'
import 'highlight.js/styles/github-dark.css'
import './style.css'

registerBuiltinWidgets()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
