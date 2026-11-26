<script setup lang="ts">
import { inject, reactive, ref } from 'vue'
import { routerKey } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import type { FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/http'

const auth = useAuthStore()
const router = inject(routerKey, undefined)
const form = reactive({ username: '', password: '' })
const formRef = ref<InstanceType<typeof ElForm>>()
const submitting = ref(false)
const errorMessage = ref('')

const rules: FormRules = {
  username: [
    { required: true, whitespace: true, message: '请输入手机号或账号', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (String(value ?? '').trim().length > 32) callback(new Error('账号长度不能超过 32 位'))
        else callback()
      },
      message: '账号长度不能超过 32 位',
      trigger: 'blur'
    }
  ],
  password: [{ required: true, min: 6, max: 20, message: '密码长度需为 6–20 位', trigger: 'blur' }]
}

async function handleSubmit() {
  if (submitting.value) return
  errorMessage.value = ''
  const username = form.username.trim()
  if (!username) {
    errorMessage.value = '请输入手机号或账号'
    return
  }
  if (username.length > 32) {
    errorMessage.value = '账号长度不能超过 32 位'
    return
  }
  if (form.password.length < 6 || form.password.length > 20) {
    errorMessage.value = '密码长度需为 6–20 位'
    return
  }
  const validation = formRef.value?.validate()
  if (validation) {
    const valid = await validation.catch(() => false)
    if (!valid || submitting.value) return
  }
  if (!formRef.value) return

  submitting.value = true
  try {
    await auth.login(username, form.password)
    if (router) await router.push(auth.homePath)
  } catch (reason) {
    errorMessage.value = reason instanceof ApiError ? reason.message : '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-view" aria-labelledby="login-title">
    <header class="auth-view-heading">
      <span class="auth-kicker">患者服务</span>
      <h1 id="login-title">欢迎回来</h1>
      <p>登录后即可预约挂号、查询订单和报告。</p>
    </header>

    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="auth-form"
      @submit.prevent="handleSubmit"
    >
      <ElFormItem label="手机号或账号" prop="username">
        <ElInput
          v-model="form.username"
          type="text"
          name="username"
          autocomplete="username"
          placeholder="请输入手机号或账号"
        />
      </ElFormItem>
      <ElFormItem label="密码" prop="password">
        <ElInput
          v-model="form.password"
          type="password"
          name="password"
          maxlength="20"
          autocomplete="current-password"
          placeholder="请输入 6–20 位密码"
          show-password
        />
      </ElFormItem>

      <ElAlert v-if="errorMessage" class="auth-error" :title="errorMessage" type="error" show-icon :closable="false" />

      <ElButton
        class="auth-submit"
        type="primary"
        native-type="submit"
        :loading="submitting"
        :disabled="submitting"
        size="large"
      >
        登录
      </ElButton>
    </ElForm>

    <p class="auth-switch">还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
  </section>
</template>

<style scoped>
.auth-view { padding: 32px 28px 30px; }
.auth-view-heading { margin-bottom: 26px; }
.auth-kicker { display: inline-block; margin-bottom: 10px; color: var(--color-brand); font-size: 13px; font-weight: 700; }
.auth-view-heading h1 { margin: 0 0 8px; color: var(--color-brand-deep); font-size: 28px; line-height: 1.3; }
.auth-view-heading p { margin: 0; color: var(--color-muted); font-size: 14px; line-height: 1.7; }
.auth-form { display: grid; gap: 4px; }
.auth-form :deep(.el-form-item) { margin-bottom: 18px; }
.auth-form :deep(.el-form-item__label) { color: var(--color-text); font-size: 14px; font-weight: 600; }
.auth-form :deep(.el-input__wrapper) { min-height: 46px; border-radius: 10px; }
.auth-error { margin-bottom: 16px; }
.auth-submit { width: 100%; min-height: 46px; border-radius: 10px; font-weight: 700; }
.auth-switch { margin: 24px 0 0; color: var(--color-muted); font-size: 14px; text-align: center; }
.auth-switch a { color: var(--color-brand); font-weight: 700; text-decoration: none; }
.auth-switch a:hover { text-decoration: underline; }

@media (max-width: 480px) {
  .auth-view { padding: 26px 20px 24px; }
  .auth-view-heading h1 { font-size: 25px; }
}
</style>
