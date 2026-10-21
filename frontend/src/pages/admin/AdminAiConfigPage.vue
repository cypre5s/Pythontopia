<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  fetchAdminAiConfig,
  testAdminAiConfig,
  updateAdminAiConfig,
  type AdminAiConfigDto,
} from '@/api/adminAiConfig';
import AdminPanel from './AdminPanel.vue';

const config = ref<AdminAiConfigDto | null>(null);
const model = ref<'deepseek-flash' | 'deepseek-v4-pro'>('deepseek-v4-pro');
const apiKey = ref('');
const loading = ref(false);
const testing = ref(false);
const error = ref('');
const message = ref('');

function keyDescription(): string {
  if (config.value?.keyStored) return '密钥已在数据库加密保存。留空会保留当前密钥。';
  if (config.value?.apiKeyConfigured) return '当前使用部署环境密钥。留空会继续使用；输入新密钥后会加密保存。';
  return '尚未配置密钥，请输入 DeepSeek API 密钥。';
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    config.value = await fetchAdminAiConfig();
    model.value = config.value.model;
  } catch (cause) {
    config.value = null;
    error.value = cause instanceof Error ? cause.message : 'AI 配置读取失败';
  } finally {
    loading.value = false;
  }
}

async function testConnection(): Promise<void> {
  if (testing.value) return;
  testing.value = true;
  error.value = '';
  message.value = '';
  try {
    const result = await testAdminAiConfig({ model: model.value, ...(apiKey.value.trim() ? { apiKey: apiKey.value.trim() } : {}) });
    message.value = result.message;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '连接测试失败';
  } finally {
    testing.value = false;
  }
}

async function save(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  error.value = '';
  message.value = '';
  try {
    config.value = await updateAdminAiConfig({ model: model.value, ...(apiKey.value.trim() ? { apiKey: apiKey.value.trim() } : {}) });
    apiKey.value = '';
    message.value = '配置已加密保存并应用到运行中的 AI 服务。';
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'AI 配置保存失败';
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <section class="admin-ai-config" data-testid="admin-ai-config" :aria-busy="loading || testing">
    <AdminPanel title="AI API 配置" description="当前服务只接入 DeepSeek。密钥仅在提交时发送，不会从后台回显；RAG 继续使用本地哈希向量。">
      <p v-if="loading && !config" role="status">正在读取 AI 配置…</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <form class="admin-ai-config__form" @submit.prevent="save">
        <label>
          <span>模型服务</span>
          <input class="admin-control" value="DeepSeek · https://api.deepseek.com" disabled>
        </label>
        <label>
          <span>默认模型</span>
          <select v-model="model" class="admin-control" data-testid="admin-ai-model" :disabled="loading || testing">
            <option value="deepseek-v4-pro">DeepSeek V4 Pro</option>
            <option value="deepseek-flash">DeepSeek Flash</option>
          </select>
        </label>
        <label class="admin-ai-config__key">
          <span>API 密钥</span>
          <input v-model="apiKey" class="admin-control" type="password" autocomplete="new-password" maxlength="512" data-testid="admin-ai-key" :disabled="loading || testing" placeholder="留空保留当前配置">
          <small>{{ keyDescription() }}</small>
        </label>
        <div class="admin-ai-config__actions">
          <button class="admin-button" type="button" data-testid="admin-ai-test" :disabled="testing || loading" @click="testConnection">{{ testing ? '测试中…' : '测试连接' }}</button>
          <button class="admin-button admin-button--primary" type="submit" data-testid="admin-ai-save" :disabled="loading || testing">{{ loading ? '保存中…' : '保存并应用' }}</button>
        </div>
      </form>
      <p v-if="message" class="admin-ai-config__message" role="status">{{ message }}</p>
      <p v-if="config" class="admin-ai-config__status">当前状态：{{ config.apiKeyConfigured ? '密钥已配置' : '密钥未配置' }} · {{ config.keyStored ? '数据库密文' : '部署环境配置' }}{{ config.updatedAt ? ` · 最近更新 ${new Date(config.updatedAt).toLocaleString('zh-CN')}` : '' }}</p>
    </AdminPanel>
  </section>
</template>

<style scoped>
.admin-ai-config__form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; max-width: 780px; }
.admin-ai-config__form label { display: grid; gap: 7px; }
.admin-ai-config__form label > span { color: var(--admin-text-strong); font-weight: 600; }
.admin-ai-config__key { grid-column: 1 / -1; }
.admin-ai-config__key small, .admin-ai-config__status { color: var(--admin-text-muted); font-size: 13px; }
.admin-ai-config__actions { display: flex; gap: 10px; }
.admin-ai-config__message { color: #166534; }
@media (max-width: 620px) { .admin-ai-config__form { grid-template-columns: 1fr; } .admin-ai-config__key { grid-column: auto; } }
</style>
