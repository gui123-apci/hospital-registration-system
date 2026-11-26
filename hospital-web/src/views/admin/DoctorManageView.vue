<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElMessage, ElSkeleton, ElTableColumn } from 'element-plus'
import { adminDepartmentApi } from '@/api/adminDepartment'
import { adminDoctorApi } from '@/api/adminDoctor'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DataTable from '@/components/DataTable.vue'
import DoctorFormDrawer from '@/components/DoctorFormDrawer.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import { useAsync } from '@/composables/useAsync'
import type { Doctor } from '@/types/models'

const PAGE_SIZE = 10
const page = ref(1)
const keywordDraft = ref('')
const appliedKeyword = ref('')
const drawerOpen = ref(false)
const editingTarget = ref<Doctor | null>(null)
const deleteTarget = ref<Doctor | null>(null)
const deleting = ref(false)
const actionError = ref('')
const actionSuccess = ref('')
const reloadWarning = ref('')

const { data, loading, error, reload } = useAsync(async () => {
  const requestedPage = page.value
  const [doctorPage, departments] = await Promise.all([
    adminDoctorApi.page(requestedPage, PAGE_SIZE),
    adminDepartmentApi.list()
  ])
  const departmentNames = new Map(departments.map((department) => [department.id, department.name]))
  return {
    ...doctorPage,
    records: doctorPage.records.map((doctor) => ({
      ...doctor,
      departmentName: departmentNames.get(doctor.departmentId) ?? '未知科室'
    }))
  }
})
type DoctorRow = Doctor & { departmentName: string }
const records = computed(() => data.value?.records ?? [])
const total = computed(() => data.value?.total ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const filteredRecords = computed(() => {
  const keyword = appliedKeyword.value.trim().toLocaleLowerCase()
  return keyword ? records.value.filter((doctor) => doctor.name.toLocaleLowerCase().includes(keyword)) : records.value
})
const deleteMessage = computed(() => deleteTarget.value
  ? `确认删除医生“${deleteTarget.value.name}”吗？删除后无法恢复。`
  : '')
const deleteVisible = computed({
  get: () => deleteTarget.value !== null,
  set: (value: boolean) => {
    if (!value && !deleting.value) deleteTarget.value = null
  }
})

watch(keywordDraft, (value) => {
  if (!value.trim()) appliedKeyword.value = ''
})

function applySearch() {
  appliedKeyword.value = keywordDraft.value.trim()
}

function clearActionFeedback() {
  actionError.value = ''
  actionSuccess.value = ''
  reloadWarning.value = ''
}

async function loadPage(nextPage = page.value, recoverOutOfRange = false) {
  page.value = nextPage
  await reload()
  if (!recoverOutOfRange || error.value || !data.value) return
  const lastPage = Math.max(1, Math.ceil(data.value.total / PAGE_SIZE))
  if (page.value > lastPage) {
    page.value = lastPage
    await reload()
  }
}

function goToPage(nextPage: number) {
  if (loading.value || deleting.value || nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) return
  clearActionFeedback()
  void loadPage(nextPage)
}

function openCreate() {
  if (deleting.value) return
  editingTarget.value = null
  drawerOpen.value = true
}

function openEdit(doctor: Doctor) {
  if (deleting.value) return
  editingTarget.value = doctor
  drawerOpen.value = true
}

function openDelete(doctor: Doctor) {
  if (deleting.value) return
  actionError.value = ''
  deleteTarget.value = doctor
}

async function refreshAfterMutation(message: string, recoverOutOfRange = false) {
  actionSuccess.value = message
  ElMessage.success(message)
  await loadPage(page.value, recoverOutOfRange)
  if (error.value) reloadWarning.value = `${message}，但医生列表刷新失败：${error.value}`
}

async function handleSaved() {
  const message = editingTarget.value ? '编辑成功' : '新增成功'
  drawerOpen.value = false
  clearActionFeedback()
  await refreshAfterMutation(message)
}

async function removeDoctor() {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleteTarget.value = null
  deleting.value = true
  clearActionFeedback()
  try {
    try {
      await adminDoctorApi.remove(target.id)
    } catch (reason) {
      actionError.value = reason instanceof Error ? reason.message : '医生删除失败，请稍后重试'
      return
    }
    await refreshAfterMutation('删除成功', true)
  } finally {
    deleting.value = false
  }
}

async function retryLoad() {
  actionError.value = ''
  reloadWarning.value = ''
  await loadPage(page.value, true)
  if (error.value && actionSuccess.value) {
    reloadWarning.value = `${actionSuccess.value}，但医生列表刷新失败：${error.value}`
  }
}

onMounted(() => { void loadPage(1) })
</script>

<template>
  <div class="doctor-page">
    <PageHeader title="医生管理" subtitle="维护医院医生信息，供患者挂号、排班与报告使用">
      <ElButton type="primary" :disabled="deleting" @click="openCreate">新增医生</ElButton>
    </PageHeader>

    <ElAlert
      v-if="actionSuccess"
      class="action-alert action-success"
      type="success"
      :title="actionSuccess"
      role="status"
      show-icon
      :closable="false"
    />
    <ElAlert
      v-if="actionError"
      class="action-alert action-error"
      type="error"
      :title="actionError"
      role="alert"
      show-icon
      :closable="false"
    />
    <div v-if="reloadWarning" class="action-alert reload-warning" role="alert">
      <div>
        <strong>列表刷新未完成</strong>
        <p>{{ reloadWarning }}</p>
      </div>
      <ElButton :loading="loading" @click="retryLoad">重新加载</ElButton>
    </div>

    <section class="doctor-panel" aria-label="医生列表" :aria-busy="loading">
      <div class="doctor-toolbar">
        <div role="search" aria-label="按医生姓名筛选当前页">
          <SearchBar
            v-model="keywordDraft"
            placeholder="搜索当前页医生姓名"
            @search="applySearch"
          />
        </div>
        <p>共 {{ total }} 位医生，每页 {{ PAGE_SIZE }} 条</p>
      </div>

      <div v-if="loading" class="doctor-loading" role="status" aria-label="正在加载医生">
        <ElSkeleton :rows="6" animated />
      </div>
      <ErrorState v-else-if="error" :message="error" @retry="retryLoad" />
      <EmptyState
        v-else-if="!filteredRecords.length"
        :description="appliedKeyword ? `当前页没有姓名包含“${appliedKeyword}”的医生` : '暂无医生，请先新增医生'"
      />
      <div v-else class="doctor-table">
        <DataTable :data="filteredRecords" empty-text="当前页无匹配医生">
          <ElTableColumn prop="name" label="医生姓名" min-width="140" fixed="left" />
          <ElTableColumn label="科室名称" min-width="140">
            <template #default="{ row }">{{ (row as DoctorRow).departmentName }}</template>
          </ElTableColumn>
          <ElTableColumn label="职称" min-width="130">
            <template #default="{ row }">{{ (row as DoctorRow).title || '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="简介" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ (row as DoctorRow).intro || '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <div class="doctor-actions">
                <ElButton
                  type="primary"
                  link
                  :disabled="deleting"
                  :aria-label="`编辑医生 ${row.name}`"
                  @click="openEdit(row as Doctor)"
                >编辑</ElButton>
                <ElButton
                  type="danger"
                  link
                  :disabled="deleting"
                  :aria-label="`删除医生 ${row.name}`"
                  @click="openDelete(row as Doctor)"
                >删除</ElButton>
              </div>
            </template>
          </ElTableColumn>
        </DataTable>
      </div>

      <nav class="doctor-pagination" aria-label="医生分页">
        <ElButton :disabled="page <= 1 || loading || deleting" @click="goToPage(page - 1)">上一页</ElButton>
        <span aria-live="polite">第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
        <ElButton :disabled="page >= totalPages || loading || deleting" @click="goToPage(page + 1)">下一页</ElButton>
      </nav>
    </section>

    <DoctorFormDrawer
      v-model="drawerOpen"
      :doctor="editingTarget"
      @saved="handleSaved"
    />
    <ConfirmDialog
      v-model="deleteVisible"
      title="确认删除医生"
      :message="deleteMessage"
      @confirm="removeDoctor"
    />
  </div>
</template>

<style scoped>
.doctor-page { padding: 26px 28px 36px; }
.doctor-page :deep(.page-header) { margin-bottom: 22px; }
.doctor-page :deep(.page-header .el-button) { min-height: 44px; border-radius: 10px; font-weight: 700; }
.action-alert { margin-bottom: 14px; }
.reload-warning { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 13px 16px; border: 1px solid #ead39a; border-radius: 10px; background: #fff9e8; color: #805d18; }
.reload-warning p { margin: 4px 0 0; font-size: 13px; line-height: 1.6; }
.reload-warning :deep(.el-button) { flex: 0 0 auto; min-height: 40px; margin-left: 0; }
.doctor-panel { min-height: 280px; }
.doctor-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.doctor-toolbar > div { width: min(100%, 430px); }
.doctor-toolbar p { flex: 0 0 auto; margin: 0; color: var(--color-muted); font-size: 12px; }
.doctor-toolbar :deep(.search-bar) { display: flex; gap: 9px; }
.doctor-toolbar :deep(.search-bar .el-input) { flex: 1; min-width: 0; }
.doctor-toolbar :deep(.search-bar .el-button) { min-height: 40px; margin-left: 0; border-radius: 9px; }
.doctor-loading { padding: 24px; border: 1px solid var(--color-border); border-radius: 12px; }
.doctor-table { overflow-x: auto; border: 1px solid var(--color-border); border-radius: 12px; }
.doctor-table :deep(.el-table) { min-width: 720px; color: var(--color-text); }
.doctor-table :deep(.el-table th.el-table__cell) { background: #f4f8f9; color: #365663; font-weight: 700; }
.doctor-table :deep(.el-table__cell) { padding: 15px 0; }
.doctor-actions { display: flex; gap: 4px; }
.doctor-actions :deep(.el-button) { min-height: 40px; margin-left: 0; padding: 0 8px; border-radius: 7px; }
.doctor-pagination { display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-top: 20px; }
.doctor-pagination :deep(.el-button) { min-height: 42px; margin-left: 0; border-radius: 9px; }
.doctor-pagination span { min-width: 180px; color: var(--color-muted); font-size: 12px; text-align: center; }
@media (max-width: 720px) {
  .doctor-page { padding: 20px 18px 28px; }
  .doctor-toolbar { align-items: stretch; flex-direction: column; gap: 10px; }
  .doctor-toolbar > div { width: 100%; }
  .doctor-pagination { justify-content: space-between; gap: 6px; }
  .doctor-pagination span { min-width: 0; }
  .reload-warning { align-items: stretch; flex-direction: column; gap: 10px; }
}
@media (max-width: 480px) {
  .doctor-page { padding: 18px 14px 24px; }
  .doctor-toolbar :deep(.search-bar) { display: grid; grid-template-columns: minmax(0, 1fr) auto; }
  .doctor-pagination { display: grid; grid-template-columns: 1fr auto 1fr; }
  .doctor-pagination :deep(.el-button) { padding: 8px 10px; }
}
</style>
