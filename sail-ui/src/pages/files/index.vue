<script setup lang="ts">
import { Upload } from '@lucide/vue'
import { onDeactivated, onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'

import type { PreviewFile } from '@/sail/file-preview'
import type { PageResult, Row } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { downloadFile, request } from '@/lib/api-client'
import AdvancedTable from '@/sail/components/AdvancedTable.vue'
import FileAttachment from '@/sail/components/FileAttachment.vue'
import FilePreview from '@/sail/components/FilePreview.vue'
import FileUpload from '@/sail/components/FileUpload.vue'
import ListPagination from '@/sail/components/ListPagination.vue'
import QueryBar from '@/sail/components/QueryBar.vue'
import SailForm from '@/sail/components/SailForm.vue'
import { useListView } from '@/sail/use-list-view'
import { useAuthStore } from '@/stores/auth'

const uploading = ref(false)
const deleting = ref<Row | null>(null)
const auth = useAuthStore()
const rows = ref<Row[]>([])
const columns = [{ key: 'originalName', label: '文件名' }, { key: 'size', label: '大小' }, { key: 'storageId', label: '存储编号' }, { key: 'publicRead', label: '可见性' }, { key: 'createdAt', label: '上传时间' }, { key: '_actions', label: '操作' }]
const { showQuery, hiddenColumns, columnOrder, leftPins, rightPins, density, visibleColumns } = useListView('files', columns)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const status = ref('')
const createdFrom = ref('')
const createdTo = ref('')
const appliedQuery = ref({ keyword: '', status: '', createdFrom: '', createdTo: '' })
/** 查询条件应用于整套数据，从第一页重新读取。 */
async function search() {
  appliedQuery.value = { keyword: keyword.value.trim(), status: status.value, createdFrom: createdFrom.value, createdTo: createdTo.value }
  page.value = 1
  await load()
}
/** 重置检索条件并同步结果。 */
async function resetSearch() {
  keyword.value = ''
  status.value = ''
  createdFrom.value = ''
  createdTo.value = ''
  await search()
}

const total = ref(0)
const busy = ref(false)
const error = ref('')
const publicRead = ref(false)
const selectedFiles = ref<File[]>([])
const preview = ref<PreviewFile>()
const progress = ref('')
/** 读取当前页面的真实数据并维护加载与错误状态。 */
async function load() {
  error.value = ''
  busy.value = true
  try {
    const result = await request<PageResult<Row>>('/file/objects', 'GET', undefined, {
      page: page.value,
      size: pageSize.value,
      keyword: appliedQuery.value.keyword,
      status: appliedQuery.value.status,
      ...(appliedQuery.value.createdFrom ? { createdFrom: appliedQuery.value.createdFrom } : {}),
      ...(appliedQuery.value.createdTo ? { createdTo: appliedQuery.value.createdTo } : {}),
    })
    rows.value = result.records
    total.value = result.total
  }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '加载失败'
    rows.value = []
    total.value = 0
  }
  finally {
    busy.value = false
  }
}
/** 上传明确选定的文件，公开下载必须由操作者显式勾选。 */
async function upload() {
  if (!selectedFiles.value.length)
    return
  busy.value = true
  error.value = ''
  const completed = new Set<File>()
  try {
    // 分组限制并发，保留失败文件供重试，成功文件不重复上传。
    const selected = [...selectedFiles.value]
    for (let offset = 0; offset < selected.length; offset += 3) {
      const results = await Promise.allSettled(selected.slice(offset, offset + 3).map(async (file) => {
        const body = new FormData()
        body.append('file', file)
        await request('/file/objects', 'POST', body, { publicRead: String(publicRead.value) })
        completed.add(file)
      }))
      selectedFiles.value = selectedFiles.value.filter(file => !completed.has(file))
      progress.value = `已上传 ${completed.size} / ${selected.length}`
      const failure = results.find(result => result.status === 'rejected')
      if (failure?.status === 'rejected')
        throw failure.reason
    }
    toast.success('文件已上传')
    uploading.value = false
    await load()
  }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '上传失败'
  }
  finally {
    busy.value = false
    progress.value = ''
  }
}
/** 携带当前登录身份下载原始文件。 */
async function download(row: Row) {
  busy.value = true
  try {
    await downloadFile(row.id, String(row.originalName))
  }
  catch (failure) {
    toast.error(failure instanceof Error ? failure.message : '下载失败')
  }
  finally {
    busy.value = false
  }
}
/** 确认具体对象后调用后端删除接口。 */
async function remove(row: Row) {
  busy.value = true
  try {
    await request(`/file/objects/${row.id}`, 'DELETE')
    toast.success('文件已删除')
    deleting.value = null
    await load()
  }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '删除失败'
  }
  finally {
    busy.value = false
  }
}
/** 切换页码并按接口分页能力读取数据。 */
async function changePage(target: number) {
  page.value = target
  await load()
}
onDeactivated(() => {
  uploading.value = false
  deleting.value = null
})
onMounted(load)
</script>

<template>
  <section class="space-y-5">
    <FilePreview :file="preview" @close="preview = undefined" />
    <AdvancedTable v-model:show-query="showQuery" v-model:hidden-columns="hiddenColumns" v-model:column-order="columnOrder" v-model:left-pins="leftPins" v-model:right-pins="rightPins" v-model:density="density" :columns="columns" title="文件列表" :total="total" :loading="busy" @refresh="load">
      <template #query>
        <QueryBar v-model:created-from="createdFrom" v-model:created-to="createdTo" v-model:keyword="keyword" v-model:status="status" dates placeholder="输入文件名称" :statuses="[{ value: 'false', label: '私有文件' }, { value: 'true', label: '公开文件' }]" :busy="busy" @search="search" @reset="resetSearch" />
      </template>
      <template #actions>
        <Button v-if="auth.can('file:upload')" size="sm" @click="uploading = true">
          <Upload class="size-3.5" />上传文件
        </Button>
      </template>
      <template #before>
        <p v-if="error" role="alert" class="text-sm text-destructive break-words">
          {{ error }}
        </p>
        <p v-if="busy" role="status" class="text-sm text-muted-foreground">
          正在处理文件…
        </p>
      </template>
      <Table>
        <TableHeader class="bg-muted/40">
          <TableRow>
            <TableHead v-for="column in visibleColumns" :key="column.key" :data-column="column.key">
              {{ column.label }}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="row in rows" :key="row.id">
            <TableCell v-for="column in visibleColumns" :key="column.key" :data-column="column.key" class="max-w-xs whitespace-normal break-words">
              <template v-if="column.key === 'originalName'">
                <FileAttachment :file="{ id: row.id, name: String(row.originalName), contentType: String(row.contentType), size: Number(row.size) }" @preview="preview = $event" />
              </template>
              <template v-else-if="column.key === 'size'">
                {{ (Number(row.size) / 1024).toFixed(1) }} KiB
              </template>
              <template v-else-if="column.key === 'storageId'">
                #{{ row.storageId }}
              </template>
              <template v-else-if="column.key === 'publicRead'">
                {{ row.publicRead ? '公开' : '私有' }}
              </template>
              <template v-else-if="column.key === 'createdAt'">
                {{ row.createdAt }}
              </template>
              <template v-else-if="column.key === '_actions'">
                <div class="sail-row-actions">
                  <Button size="sm" variant="ghost" @click="preview = { id: row.id, name: String(row.originalName), contentType: String(row.contentType), size: Number(row.size) }">
                    预览
                  </Button>
                  <Button size="sm" variant="ghost" :disabled="busy" @click="download(row)">
                    下载
                  </Button>
                  <Button
                    v-if="auth.can('file:delete')"
                    size="sm"
                    variant="ghost" class="text-destructive"
                    :disabled="busy"
                    @click="deleting = row"
                  >
                    删除
                  </Button>
                </div>
              </template>
            </TableCell>
          </TableRow>
          <TableRow v-if="!rows.length && !busy">
            <TableCell :colspan="visibleColumns.length" class="h-24 text-center text-muted-foreground">
              {{ error ? '文件加载失败，请重试。' : '暂无文件，可上传第一个附件。' }}
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
      <template #pagination>
        <ListPagination v-model:size="pageSize" :page="page" :total="total" :busy="busy" @change="changePage" />
      </template>
    </AdvancedTable>
    <Dialog v-model:open="uploading">
      <DialogContent>
        <DialogHeader><DialogTitle>上传文件</DialogTitle><DialogDescription>单文件不超过 20 MiB，使用当前默认存储。</DialogDescription></DialogHeader>
        <SailForm
          v-if="auth.can('file:upload')"
          class="space-y-5"
          @submit.prevent="upload"
        >
          <FileUpload v-model="selectedFiles" :disabled="busy" />
          <label class="flex items-center gap-2 text-sm">
            <Checkbox v-model="publicRead" :disabled="busy" />
            允许匿名公开下载
          </label>
          <Button type="submit" :disabled="busy || !selectedFiles.length">
            {{ busy ? progress || '正在上传…' : `上传文件${selectedFiles.length ? `（${selectedFiles.length}）` : ''}` }}
          </Button>
        </SailForm>
        <p v-if="error" role="alert" class="text-sm text-destructive">
          {{ error }}
        </p>
      </DialogContent>
    </Dialog>
    <Dialog :open="Boolean(deleting)" @update:open="!$event && (deleting = null)">
      <DialogContent>
        <DialogHeader><DialogTitle>删除文件</DialogTitle><DialogDescription>确认删除「{{ deleting?.originalName }}」？实际存储对象也会删除。</DialogDescription></DialogHeader><div class="flex justify-end gap-2">
          <Button variant="outline" :disabled="busy" @click="deleting = null">
            取消
          </Button><Button variant="destructive" :disabled="busy" @click="deleting && remove(deleting)">
            确认删除
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  </section>
</template>

<route lang="yaml">
meta:
  sail: true
  auth: true
  permission: file:query
  title: 文件管理
</route>
