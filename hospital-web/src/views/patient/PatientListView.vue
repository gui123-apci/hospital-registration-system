<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElMessage, ElSkeleton } from 'element-plus'
import { patientApi } from '@/api/patient'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PageHeader from '@/components/PageHeader.vue'
import PatientFormDrawer from '@/components/PatientFormDrawer.vue'
import { useAsync } from '@/composables/useAsync'
import { maskIdCard, relationLabel } from '@/utils/patient'
import type { Patient } from '@/types/models'

const { data, loading, error, reload } = useAsync(() => patientApi.list())
const patients = computed(() => data.value ?? [])
const drawerVisible = ref(false)
const editingPatient = ref<Patient | null>(null)
const successMessage = ref('')
const actionError = ref('')
const deleteTarget = ref<Patient | null>(null)
const deleting = ref(false)

const deleteVisible = computed({
  get: () => deleteTarget.value !== null,
  set: (value: boolean) => {
    if (!value) deleteTarget.value = null
  }
})
const deleteMessage = computed(() => deleteTarget.value
  ? `确认删除就诊人 ${deleteTarget.value.name} 吗？删除后不可恢复，已有挂号记录的就诊人无法删除。`
  : '')

function openCreate() {
  editingPatient.value = null
  successMessage.value = ''
  actionError.value = ''
  drawerVisible.value = true
}

function openEdit(patient: Patient) {
  editingPatient.value = patient
  successMessage.value = ''
  actionError.value = ''
  drawerVisible.value = true
}

async function handleSaved(patient: Patient) {
  drawerVisible.value = false
  editingPatient.value = null
  successMessage.value = `已保存就诊人 ${patient.name}`
  await reload()
}

async function deletePatient() {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleteTarget.value = null
  deleting.value = true
  actionError.value = ''
  successMessage.value = ''
  try {
    await patientApi.remove(target.id)
  } catch (reason) {
    actionError.value = reason instanceof Error ? reason.message : '删除失败，请稍后重试'
    return
  } finally {
    deleting.value = false
  }
  successMessage.value = `已删除就诊人 ${target.name}`
  ElMessage.success('删除成功')
  await reload()
}

onMounted(reload)
</script>

<template>
  <div class="patients-page">
    <PageHeader title="就诊人管理" subtitle="管理常用就诊人，挂号时可直接选择">
      <ElButton type="primary" class="add-button" @click="openCreate">新增就诊人</ElButton>
    </PageHeader>

    <ElAlert
      v-if="successMessage"
      class="success-alert"
      type="success"
      :title="successMessage"
      show-icon
      :closable="false"
    />
    <ElAlert
      v-if="actionError"
      class="success-alert"
      type="error"
      :title="actionError"
      show-icon
      :closable="false"
    />

    <section class="patients-panel" aria-label="就诊人列表" :aria-busy="loading">
      <div v-if="loading" class="loading-list" role="status" aria-label="正在加载就诊人">
        <ElSkeleton :rows="4" animated />
        <ElSkeleton :rows="4" animated />
      </div>
      <ErrorState v-else-if="error" :message="error" @retry="reload" />
      <div v-else-if="!patients.length" class="patients-empty">
        <EmptyState description="暂无就诊人，挂号前需先添加就诊人（真实姓名、身份证）" />
        <ElButton type="primary" class="empty-add-button" @click="openCreate">添加就诊人</ElButton>
      </div>
      <div v-else class="patient-list">
        <article v-for="patient in patients" :key="patient.id" class="patient-card">
          <div class="patient-heading">
            <div class="patient-title">
              <span class="patient-avatar" aria-hidden="true">{{ patient.name.slice(0, 1) }}</span>
              <div>
                <strong class="patient-name">{{ patient.name }}</strong>
                <span v-if="patient.name === '待完善'" class="incomplete-tag">请完善姓名</span>
                <span class="relation-tag">{{ relationLabel(patient.relation) }}</span>
              </div>
            </div>
            <div class="patient-actions">
              <ElButton class="edit-button" text type="primary" :aria-label="`编辑就诊人 ${patient.name}`" @click="openEdit(patient)">
                编辑
              </ElButton>
              <ElButton
                class="delete-button"
                text
                type="danger"
                :disabled="deleting"
                :aria-label="`删除就诊人 ${patient.name}`"
                @click="deleteTarget = patient"
              >删除</ElButton>
            </div>
          </div>

          <dl class="patient-details">
            <div>
              <dt>手机号</dt>
              <dd>{{ patient.phone || '未填写' }}</dd>
            </div>
            <div>
              <dt>身份证号</dt>
              <dd class="identity-value">{{ maskIdCard(patient.idCard) }}</dd>
            </div>
          </dl>
        </article>
      </div>
    </section>

    <PatientFormDrawer
      v-model="drawerVisible"
      :patient="editingPatient"
      @saved="handleSaved"
    />

    <ConfirmDialog v-model="deleteVisible" :message="deleteMessage" title="确认删除" @confirm="deletePatient" />
  </div>
</template>

<style scoped>
.patients-page { width: min(100%, 720px); margin: 0 auto; padding: 24px 16px 32px; }
.patients-page :deep(.page-header) { margin-bottom: 20px; }
.patients-page :deep(.page-header h1) { overflow-wrap: anywhere; }
.add-button { min-height: 42px; padding: 10px 16px; border-radius: 10px; font-weight: 700; white-space: nowrap; }
.success-alert { margin-bottom: 14px; }
.patients-panel { min-height: 180px; }
.patients-empty { display: grid; justify-items: center; gap: 4px; }
.empty-add-button { min-height: 42px; padding: 10px 18px; border-radius: 10px; font-weight: 700; }
.loading-list { display: grid; gap: 22px; padding: 22px; border: 1px solid var(--color-border); border-radius: 16px; background: white; }
.patient-list { display: grid; gap: 14px; }
.patient-card { padding: 18px; border: 1px solid var(--color-border); border-radius: 16px; background: white; box-shadow: 0 8px 24px rgba(18, 57, 74, .04); }
.patient-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-bottom: 15px; border-bottom: 1px solid #edf2f4; }
.patient-title { display: flex; min-width: 0; align-items: center; gap: 12px; }
.patient-title > div { display: flex; min-width: 0; flex-wrap: wrap; align-items: center; gap: 8px; }
.patient-avatar { display: grid; width: 42px; height: 42px; flex: 0 0 auto; place-items: center; border-radius: 12px; background: var(--color-brand-soft); color: var(--color-brand-deep); font-size: 17px; font-weight: 700; }
.patient-name { overflow-wrap: anywhere; color: var(--color-brand-deep); font-size: 17px; }
.relation-tag { padding: 3px 8px; border-radius: 6px; background: var(--color-brand-soft); color: var(--color-brand); font-size: 12px; }
.incomplete-tag { padding: 3px 8px; border-radius: 6px; background: #fef0f0; color: #f56c6c; font-size: 12px; }
.edit-button { min-height: 40px; margin-left: 0; padding: 8px 4px; }
.patient-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 4px; }
.delete-button { min-height: 40px; margin-left: 0; padding: 8px 4px; }
.patient-details { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 20px; margin: 16px 0 0; }
.patient-details > div { display: grid; min-width: 0; gap: 5px; }
.patient-details dt { color: var(--color-muted); font-size: 12px; }
.patient-details dd { margin: 0; overflow-wrap: anywhere; color: var(--color-text); font-size: 14px; font-weight: 600; line-height: 1.5; }
.identity-value { font-variant-numeric: tabular-nums; letter-spacing: .02em; }
@media (max-width: 560px) {
  .patients-page { padding: 20px 14px 28px; }
  .patients-page :deep(.page-header) { align-items: flex-start; gap: 12px; }
  .add-button { padding: 9px 12px; font-size: 13px; }
  .patient-card { padding: 16px 14px; }
  .patient-details { grid-template-columns: 1fr; }
}
</style>
