<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElMessage, ElSkeleton, ElTableColumn } from 'element-plus'
import { adminDepartmentApi } from '@/api/adminDepartment'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DataTable from '@/components/DataTable.vue'
import DepartmentFormDrawer from '@/components/DepartmentFormDrawer.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import { useAsync } from '@/composables/useAsync'
import type { Department } from '@/types/models'

const PAGE_SIZE = 10
const page = ref(1)
const keywordDraft = ref('')
const appliedKeyword = ref('')
const drawerOpen = ref(false)
const editingTarget = ref<Department | null>(null)
const deleteTarget = ref<Department | null>(null)
const deleting = ref(false)
const actionError = ref('')
const actionSuccess = ref('')
const reloadWarning = ref('')

const { data, loading, error, reload } = useAsync(() => adminDepartmentApi.page(page.value, PAGE_SIZE))
const records = computed(() => data.value?.records ?? [])
const total = computed(() => data.value?.total ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const filteredRecords = computed(() => {
  const keyword = appliedKeyword.value.trim().toLocaleLowerCase()
  return keyword ? records.value.filter((department) => department.name.toLocaleLowerCase().includes(keyword)) : records.value
})
const deleteMessage = computed(() => deleteTarget.value
  ? `确认删除科室“${deleteTarget.value.name}”吗？删除后无法恢复。`
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

function openEdit(department: Department) {
  if (deleting.value) return
  editingTarget.value = department
  drawerOpen.value = true
}

function openDelete(department: Department) {
  if (deleting.value) return
  actionError.value = ''
  deleteTarget.value = department
}

async function refreshAfterMutation(message: string, recoverOutOfRange = false) {
  actionSuccess.value = message
  ElMessage.success(message)
  await loadPage(page.value, recoverOutOfRange)
  if (error.value) reloadWarning.value = `${message}，但科室列表刷新失败：${error.value}`
}

async function handleSaved() {
  const message = editingTarget.value ? '编辑成功' : '新增成功'
  drawerOpen.value = false
  clearActionFeedback()
  await refreshAfterMutation(message)
}

async function removeDepartment() {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleteTarget.value = null
  deleting.value = true
  clearActionFeedback()
  try {
    try {
      await adminDepartmentApi.remove(target.id)
    } catch (reason) {
      actionError.value = reason instanceof Error ? reason.message : '科室删除失败，请稍后重试'
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
    reloadWarning.value = `${actionSuccess.value}，但科室列表刷新失败：${error.value}`
  }
}

onMounted(() => { void loadPage(1) })
</script>

<template>
  <div class="department-page">
    <PageHeader title="科室管理" subtitle="维护医院科室信息，供医生、排班与挂号使用">
      <ElButton type="primary" :disabled="deleting" @click="openCreate">新增科室</ElButton>
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

    <section class="department-panel" aria-label="科室列表" :aria-busy="loading">
      <div class="department-toolbar">
        <div role="search" aria-label="按科室名称筛选当前页">
          <SearchBar
            v-model="keywordDraft"
            placeholder="搜索当前页科室名称"
            @search="applySearch"
          />
        </div>
        <p>共 {{ total }} 个科室，每页 {{ PAGE_SIZE }} 条</p>
      </div>

      <div v-if="loading" class="department-loading" role="status" aria-label="正在加载科室">
        <ElSkeleton :rows="6" animated />
      </div>
      <ErrorState v-else-if="error" :message="error" @retry="retryLoad" />
      <EmptyState
        v-else-if="!filteredRecords.length"
        :description="appliedKeyword ? `当前页没有名称包含“${appliedKeyword}”的科室` : '暂无科室，请先新增科室'"
      />
      <div v-else class="department-table">
        <DataTable :data="filteredRecords" :empty-text="appliedKeyword ? '当前页无匹配科室' : '暂无科室'">
          <ElTableColumn prop="id" label="编号" width="90" />
          <ElTableColumn prop="name" label="科室名称" min-width="160" />
          <ElTableColumn prop="description" label="简介" min-width="260">
            <template #default="{ row }">{{ (row as Department).description || '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <div class="department-actions">
                <ElButton
                  type="primary"
                  link
                  :disabled="deleting"
                  :aria-label="`编辑科室 ${row.name}`"
                  @click="openEdit(row as Department)"
                >编辑</ElButton>
                <ElButton
                  type="danger"
                  link
                  :disabled="deleting"
                  :aria-label="`删除科室 ${row.name}`"
                  @click="openDelete(row as Department)"
                >删除</ElButton>
              </div>
            </template>
          </ElTableColumn>
        </DataTable>
      </div>

      <nav class="department-pagination" aria-label="科室分页">
        <ElButton :disabled="page <= 1 || loading || deleting" @click="goToPage(page - 1)">上一页</ElButton>
        <span aria-live="polite">第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
        <ElButton :disabled="page >= totalPages || loading || deleting" @click="goToPage(page + 1)">下一页</ElButton>
      </nav>
    </section>

    <DepartmentFormDrawer
      v-model="drawerOpen"
      :department="editingTarget"
      @saved="handleSaved"
    />
    <ConfirmDialog
      v-model="deleteVisible"
      title="确认删除科室"
      :message="deleteMessage"
      @confirm="removeDepartment"
    />
  </div>
</template>

<style scoped>
.department-page { padding: 26px 28px 36px; }
.department-page :deep(.page-header) { margin-bottom: 22px; }
.department-page :deep(.page-header .el-button) { min-height: 44px; border-radius: 10px; font-weight: 700; }
.action-alert { margin-bottom: 14px; }
.reload-warning { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 13px 16px; border: 1px solid #ead39a; border-radius: 10px; background: #fff9e8; color: #805d18; }
.reload-warning p { margin: 4px 0 0; font-size: 13px; line-height: 1.6; }
.reload-warning :deep(.el-button) { flex: 0 0 auto; min-height: 40px; margin-left: 0; }
.department-panel { min-height: 280px; }
.department-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.department-toolbar > div { width: min(100%, 430px); }
.department-toolbar p { flex: 0 0 auto; margin: 0; color: var(--color-muted); font-size: 12px; }
.department-toolbar :deep(.search-bar) { display: flex; gap: 9px; }
.department-toolbar :deep(.search-bar .el-input) { flex: 1; min-width: 0; }
.department-toolbar :deep(.search-bar .el-button) { min-height: 40px; margin-left: 0; border-radius: 9px; }
.department-loading { padding: 24px; border: 1px solid var(--color-border); border-radius: 12px; }
.department-table { overflow-x: auto; border: 1px solid var(--color-border); border-radius: 12px; }
.department-table :deep(.el-table) { min-width: 620px; color: var(--color-text); }
.department-table :deep(.el-table th.el-table__cell) { background: #f4f8f9; color: #365663; font-weight: 700; }
.department-table :deep(.el-table__cell) { padding: 15px 0; }
.department-actions { display: flex; gap: 4px; }
.department-actions :deep(.el-button) { min-height: 40px; margin-left: 0; padding: 0 8px; border-radius: 7px; }
.department-pagination { display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-top: 20px; }
.department-pagination :deep(.el-button) { min-height: 42px; margin-left: 0; border-radius: 9px; }
.department-pagination span { min-width: 180px; color: var(--color-muted); font-size: 12px; text-align: center; }
@media (max-width: 720px) {
  .department-page { padding: 20px 18px 28px; }
  .department-toolbar { align-items: stretch; flex-direction: column; gap: 10px; }
  .department-toolbar > div { width: 100%; }
  .department-pagination { justify-content: space-between; gap: 6px; }
  .department-pagination span { min-width: 0; }
  .reload-warning { align-items: stretch; flex-direction: column; gap: 10px; }
}
@media (max-width: 480px) {
  .department-page { padding: 18px 14px 24px; }
  .department-toolbar :deep(.search-bar) { display: grid; grid-template-columns: minmax(0, 1fr) auto; }
  .department-pagination { display: grid; grid-template-columns: 1fr auto 1fr; }
  .department-pagination :deep(.el-button) { padding: 8px 10px; }
}
</style>
