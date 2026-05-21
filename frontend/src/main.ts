import { createPinia } from 'pinia';
import { createApp } from 'vue';
import App from './App.vue';
import { router } from './app/router';
import { useSessionStore } from './stores/session';
import './styles/tokens.css';
import './styles.css';

const pinia = createPinia();
const app = createApp(App);

app.use(pinia);
app.use(router);
const session = useSessionStore();
session.hydrateFromStorage();
session.configureHttp(() => {
  void router.push({ name: 'login' });
});
app.mount('#app');
