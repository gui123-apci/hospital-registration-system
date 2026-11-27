<script setup lang="ts">
import { inject, reactive, ref } from 'vue'
import { routerKey } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import type { FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/http'

const auth = useAuthStore()
const router = inject(routerKey, undefined)
const form = reactive({ phone: '', password: '', confirmPassword: '' })
const formRef = ref<InstanceType<typeof ElForm>>()
const submitting = ref(false)
const errorMessage = ref('')
const phonePattern = /^1[3-9]\d{9}$/

const rules: FormRules = {
  phone: [{ required: true, pattern: phonePattern, message: '请输入正确手机号', trigger: 'blur' }],
  password: [{ required: true, min: 6, max: 20, message: '密码长度需为 6–20 位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function handleSubmit() {
  errorMessage.value = ''
  if (!phonePattern.test(form.phone)) {
    errorMessage.value = '请输入正确手机号'
    return
  }
  if (form.password.length < 6 || form.password.length > 20) {
    errorMessage.value = '密码长度需为 6–20 位'
    return
  }
  if (!form.confirmPassword) {
    errorMessage.value = '请再次输入密码'
    return
  }
  if (form.confirmPassword !== form.password) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }
  const validation = formRef.value?.validate()
  if (validation) await validation.catch(() => false)
  if (!formRef.value || submitting.value) return

  submitting.value = true
  try {
    await auth.register(form.phone, form.password)
    // 注册只建账号，不自动建就诊人：直接送到就诊人管理页，先建档（真实姓名/身份证）再挂号
    ElMessage.success('注册成功，请先添加就诊人')
    if (router) await router.push('/patient/patients')
  } catch (reason) {
    errorMessage.value = reason instanceof ApiError ? reason.message : '注册失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-view" aria-labelledby="register-title">
    <header class="auth-view-heading">
      <span class="auth-kicker">患者服务</span>
      <h1 id="register-title">创建账号</h1>
      <p>注册智慧医院账号，开始便捷就诊。</p>
    </header>

    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="auth-form"
      @submit.prevent="handleSubmit"
    >
      <ElFormItem label="手机号" prop="phone">
        <ElInput
          v-model="form.phone"
          type="tel"
          name="phone"
          inputmode="numeric"
          maxlength="11"
          autocomplete="username"
          placeholder="请输入 11 位手机号"
        />
      </ElFormItem>
      <ElFormItem label="密码" prop="password">
        <ElInput
          v-model="form.password"
          type="password"
          name="password"
          maxlength="20"
          autocomplete="new-password"
          placeholder="请设置 6–20 位密码"
          show-password
        />
      </ElFormItem>
      <ElFormItem label="确认密码" prop="confirmPassword">
        <ElInput
          v-model="form.confirmPassword"
          type="password"
          name="confirmPassword"
          maxlength="20"
          autocomplete="new-password"
          placeholder="请再次输入密码"
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
        注册并登录
      </ElButton>
    </ElForm>

    <p class="auth-switch">已有账号？<RouterLink to="/login">返回登录</RouterLink></p>
  </section>
</template>

<style scoped>
.auth-view { padding: 32px 28px 30px; }
.auth-view-heading { margin-bottom: 24px; }
.auth-kicker { display: inline-block; margin-bottom: 10px; color: var(--color-brand); font-size: 13px; font-weight: 700; }
.auth-view-heading h1 { margin: 0 0 8px; color: var(--color-brand-deep); font-size: 28px; line-height: 1.3; }
.auth-view-heading p { margin: 0; color: var(--color-muted); font-size: 14px; line-height: 1.7; }
.auth-form { display: grid; gap: 2px; }
.auth-form :deep(.el-form-item) { margin-bottom: 16px; }
.auth-form :deep(.el-form-item__label) { color: var(--color-text); font-size: 14px; font-weight: 600; }
.auth-form :deep(.el-input__wrapper) { min-height: 46px; border-radius: 10px; }
.auth-error { margin-bottom: 16px; }
.auth-submit { width: 100%; min-height: 46px; border-radius: 10px; font-weight: 700; }
.auth-switch { margin: 22px 0 0; color: var(--color-muted); font-size: 14px; text-align: center; }
.auth-switch a { color: var(--color-brand); font-weight: 700; text-decoration: none; }
.auth-switch a:hover { text-decoration: underline; }

@media (max-width: 480px) {
  .auth-view { padding: 26px 20px 24px; }
  .auth-view-heading h1 { font-size: 25px; }
}
</style>
