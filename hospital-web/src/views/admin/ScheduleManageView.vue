<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElMessage, ElSkeleton, ElTableColumn } from 'element-plus'
import { adminDepartmentApi } from '@/api/adminDepartment'
import { adminDoctorApi } from '@/api/adminDoctor'
import { adminScheduleApi } from '@/api/adminSchedule'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DataTable from '@/components/DataTable.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import NameIdSelect, { type NameIdOption } from '@/components/NameIdSelect.vue'
import PageHeader from '@/components/PageHeader.vue'
import ScheduleFormDrawer from '@/components/ScheduleFormDrawer.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAsync } from '@/composables/useAsync'
import type { Schedule } from '@/types/models'
import { scheduleLabel, timeSlotLabel } from '@/utils/schedule'

const PAGE_SIZE = 10
const page = ref(1)
const doctorDraft = ref<number>()
const appliedDoctorId = ref<number>()
const drawerOpen = ref(false)
const editingTarget = ref<Schedule | null>(null)
const deleteTarget = ref<ScheduleRow | null>(null)
const deleting = ref(false)
const actionError = ref('')
const actionSuccess = ref('')
const reloadWarning = ref('')

const { data, loading, error, reload } = useAsync(async () => {
  const [schedulePage, doctors, departments] = await Promise.all([
    adminScheduleApi.page(page.value, PAGE_SIZE, appliedDoctorId.value),
    adminDoctorApi.list(),
    adminDepartmentApi.list()
  ])
  const doctorNames = new Map(doctors.map((doctor) => [doctor.id, doctor.name]))
  const departmentNames = new Map(departments.map((department) => [department.id, department.name]))
  return {
    ...schedulePage,
    doctors,
    records: schedulePage.records.map((schedule) => ({
      ...schedule,
      doctorName: doctorNames.get(schedule.doctorId) ?? '未知医生',
      departmentName: departmentNames.get(schedule.departmentId) ?? '未知科室'
    }))
  }
})

type ScheduleRow = Schedule & { doctorName: string; departmentName: string }
const records = computed(() => data.value?.records ?? [])
const doctorOptions = computed<NameIdOption[]>(() => data.value?.doctors.map((doctor) => ({ label: doctor.name, value: doctor.id })) ?? [])
const total = computed(() => data.value?.total ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const deleteMessage = computed(() => deleteTarget.value
  ? `确认删除 ${scheduleLabel(deleteTarget.value, deleteTarget.value.doctorName)} 的排班吗？删除后无法恢复。`
  : '')
const deleteVisible = computed({
  get: () => deleteTarget.value !== null,
  set: (value: boolean) => {
    if (!value && !deleting.value) deleteTarget.value = null
  }
})

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

function applySearch() {
  if (loading.value || deleting.value) return
  appliedDoctorId.value = doctorDraft.value
  clearActionFeedback()
  void loadPage(1)
}

function resetSearch() {
  if (loading.value || deleting.value) return
  doctorDraft.value = undefined
  appliedDoctorId.value = undefined
  clearActionFeedback()
  void loadPage(1)
}

function openCreate() {
  if (deleting.value) return
  editingTarget.value = null
  drawerOpen.value = true
}

function openEdit(schedule: Schedule) {
  if (deleting.value) return
  editingTarget.value = schedule
  drawerOpen.value = true
}

function openDelete(schedule: ScheduleRow) {
  if (deleting.value) return
  actionError.value = ''
  deleteTarget.value = schedule
}

async function refreshAfterMutation(message: string, recoverOutOfRange = false) {
  actionSuccess.value = message
  ElMessage.success(message)
  await loadPage(page.value, recoverOutOfRange)
  if (error.value) reloadWarning.value = `${message}，但排班列表刷新失败：${error.value}`
}

async function handleSaved() {
  const message = editingTarget.value ? '编辑成功' : '新增成功'
  drawerOpen.value = false
  clearActionFeedback()
  await refreshAfterMutation(message)
}

async function removeSchedule() {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleteTarget.value = null
  deleting.value = true
  clearActionFeedback()
  try {
    try {
      await adminScheduleApi.remove(target.id)
    } catch (reason) {
      actionError.value = reason instanceof Error ? reason.message : '排班删除失败，请稍后重试'
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
    reloadWarning.value = `${actionSuccess.value}，但排班列表刷新失败：${error.value}`
  }
}

onMounted(() => { void loadPage(1) })
</script>

<template>
  <div class="schedule-page">
    <PageHeader title="排班管理" subtitle="维护医生出诊日期、时段、号源与挂号费用">
      <ElButton type="primary" :disabled="deleting" @click="openCreate">新增排班</ElButton>
    </PageHeader>

    <ElAlert v-if="actionSuccess" class="action-alert action-success" type="success" :title="actionSuccess" role="status" show-icon :closable="false" />
    <ElAlert v-if="actionError" class="action-alert action-error" type="error" :title="actionError" role="alert" show-icon :closable="false" />
    <div v-if="reloadWarning" class="action-alert reload-warning" role="alert">
      <div><strong>列表刷新未完成</strong><p>{{ reloadWarning }}</p></div>
      <ElButton :loading="loading" @click="retryLoad">重新加载</ElButton>
    </div>

    <section class="schedule-panel" aria-label="排班列表" :aria-busy="loading">
      <div class="schedule-toolbar">
        <div class="doctor-filter" role="search" aria-label="按医生筛选排班">
          <NameIdSelect
            v-model="doctorDraft"
            name="doctorFilter"
            :options="doctorOptions"
            placeholder="按医生筛选"
            :disabled="loading || deleting"
            aria-label="按医生筛选"
          />
          <ElButton :disabled="loading || deleting" @click="applySearch">查询</ElButton>
          <ElButton :disabled="loading || deleting" @click="resetSearch">重置</ElButton>
        </div>
        <p>共 {{ total }} 条排班，每页 {{ PAGE_SIZE }} 条</p>
      </div>

      <div v-if="loading" class="schedule-loading" role="status" aria-label="正在加载排班">
        <ElSkeleton :rows="6" animated />
      </div>
      <ErrorState v-else-if="error" :message="error" @retry="retryLoad" />
      <EmptyState v-else-if="!records.length" description="暂无排班，请先新增排班" />
      <div v-else class="schedule-table">
        <DataTable :data="records" empty-text="暂无排班">
          <ElTableColumn label="编号" width="80" fixed="left">
            <template #default="{ row }">#{{ (row as ScheduleRow).id }}</template>
          </ElTableColumn>
          <ElTableColumn prop="doctorName" label="医生姓名" min-width="120" fixed="left" />
          <ElTableColumn prop="departmentName" label="科室名称" min-width="120" />
          <ElTableColumn prop="workDate" label="出诊日期" min-width="130" />
          <ElTableColumn label="时段" min-width="90">
            <template #default="{ row }">{{ timeSlotLabel((row as ScheduleRow).timeSlot) }}</template>
          </ElTableColumn>
          <ElTableColumn prop="totalNum" label="总号数" min-width="90" />
          <ElTableColumn prop="leftNum" label="剩余号数" min-width="100" />
          <ElTableColumn label="挂号费" min-width="110">
            <template #default="{ row }">¥{{ Number((row as ScheduleRow).price).toFixed(2) }}</template>
          </ElTableColumn>
          <ElTableColumn label="状态" min-width="100">
            <template #default="{ row }">
              <StatusTag :type="(row as ScheduleRow).status === 1 ? 'success' : 'info'" :text="(row as ScheduleRow).status === 1 ? '可预约' : '停诊'" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="schedule-actions">
                <ElButton type="primary" link :disabled="deleting" :aria-label="`编辑排班 ${(row as ScheduleRow).id}`" @click="openEdit(row as ScheduleRow)">编辑</ElButton>
                <ElButton type="danger" link :disabled="deleting" :aria-label="`删除排班 ${(row as ScheduleRow).id}`" @click="openDelete(row as ScheduleRow)">删除</ElButton>
              </div>
            </template>
          </ElTableColumn>
        </DataTable>
      </div>

      <nav class="schedule-pagination" aria-label="排班分页">
        <ElButton :disabled="page <= 1 || loading || deleting" @click="goToPage(page - 1)">上一页</ElButton>
        <span aria-live="polite">第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
        <ElButton :disabled="page >= totalPages || loading || deleting" @click="goToPage(page + 1)">下一页</ElButton>
      </nav>
    </section>

    <ScheduleFormDrawer v-model="drawerOpen" :schedule="editingTarget" @saved="handleSaved" />
    <ConfirmDialog v-model="deleteVisible" title="确认删除排班" :message="deleteMessage" @confirm="removeSchedule" />
  </div>
</template>

<style scoped>
.schedule-page { padding: 26px 28px 36px; }
.schedule-page :deep(.page-header) { margin-bottom: 22px; }
.schedule-page :deep(.page-header .el-button) { min-height: 44px; border-radius: 10px; font-weight: 700; }
.action-alert { margin-bottom: 14px; }
.reload-warning { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 13px 16px; border: 1px solid #ead39a; border-radius: 10px; background: #fff9e8; color: #805d18; }
.reload-warning p { margin: 4px 0 0; font-size: 13px; line-height: 1.6; }
.reload-warning :deep(.el-button) { flex: 0 0 auto; min-height: 40px; margin-left: 0; }
.schedule-panel { min-height: 280px; }
.schedule-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.doctor-filter { display: flex; width: min(100%, 520px); gap: 9px; }
.doctor-filter :deep(.el-select) { flex: 1; min-width: 0; }
.doctor-filter :deep(.el-button) { min-height: 40px; margin-left: 0; border-radius: 9px; }
.schedule-toolbar p { flex: 0 0 auto; margin: 0; color: var(--color-muted); font-size: 12px; }
.schedule-loading { padding: 24px; border: 1px solid var(--color-border); border-radius: 12px; }
.schedule-table { overflow-x: auto; border: 1px solid var(--color-border); border-radius: 12px; }
.schedule-table :deep(.el-table) { min-width: 1000px; color: var(--color-text); }
.schedule-table :deep(.el-table th.el-table__cell) { background: #f4f8f9; color: #365663; font-weight: 700; }
.schedule-table :deep(.el-table__cell) { padding: 15px 0; }
.schedule-actions { display: flex; gap: 4px; }
.schedule-actions :deep(.el-button) { min-height: 40px; margin-left: 0; padding: 0 8px; border-radius: 7px; }
.schedule-pagination { display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-top: 20px; }
.schedule-pagination :deep(.el-button) { min-height: 42px; margin-left: 0; border-radius: 9px; }
.schedule-pagination span { min-width: 180px; color: var(--color-muted); font-size: 12px; text-align: center; }
@media (max-width: 720px) {
  .schedule-page { padding: 20px 18px 28px; }
  .schedule-toolbar { align-items: stretch; flex-direction: column; gap: 10px; }
  .doctor-filter { width: 100%; }
  .schedule-pagination { justify-content: space-between; gap: 6px; }
  .schedule-pagination span { min-width: 0; }
  .reload-warning { align-items: stretch; flex-direction: column; gap: 10px; }
}
@media (max-width: 480px) {
  .schedule-page { padding: 18px 14px 24px; }
  .doctor-filter { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; }
  .schedule-pagination { display: grid; grid-template-columns: 1fr auto 1fr; }
  .schedule-pagination :deep(.el-button) { padding: 8px 10px; }
}
</style>
