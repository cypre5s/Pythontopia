import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./app/App.vue";
import { router } from "./app/router";
import "./app/styles/base.css";
import "./features/world/worldUi.css";
import "./app/styles/credits.css";
createApp(App).use(createPinia()).use(router).mount("#app");
