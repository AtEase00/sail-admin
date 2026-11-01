<script setup lang="ts">
import { ChevronDown, ChevronRight, ChevronsDownUp, ChevronsUpDown, Folder, Plus } from '@lucide/vue'
import { computed, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'

import type { QueryField } from '@/sail/list-models'
import type { Field } from '@/sail/resources'
import type { TreeItem } from '@/sail/tree'
import type { PageResult, Row } from '@/services/types/sail'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { request } from '@/lib/api-client'
import ResourceActions from '@/sail/components/ResourceActions.vue'
import SailForm from '@/sail/components/SailForm.vue'
import { resolveMenuIcon } from '@/sail/navigation-icons'
import { display, protectedRow, queryOptions, resources } from '@/sail/resources'
import { branchIds, buildTree } from '@/sail/tree'
import { useListView } from '@/sail/use-list-view'
import { useAuthStore } from '@/stores/auth'
import { useDictionaryStore } from '@/stores/dictionaries'

import AdvancedTable from './AdvancedTable.vue'
import FormSelect from './FormSelect.vue'
import IconPicker from './IconPicker.vue'
import ListPagination from './ListPagination.vue'
import QueryBar from './QueryBar.vue'
import RolePermissions from './RolePermissions.vue'

const props = defineProps<{ resource: string, fixedQuery?: Record<string, string> }>()
const config = resources[props.resource]
const auth = useAuthStore()
const dictionaries = useDictionaryStore()
const canWrite = computed(
  () =>
    Boolean(config.fields) && (config.adminWrite ? auth.isAdmin : auth.can(config.writePermission)),
)
const columns = [...config.columns.filter(column => !(props.fixedQuery && column.key in props.fixedQuery)), { key: '_actions', label: '操作' }]
const { showQuery, hiddenColumns, columnOrder, leftPins, rightPins, density, visibleColumns } = useListView(props.resource, columns)
const queryValues = ref<Record<string, string>>({})
const appliedValues = ref<Record<string, string>>({})
const queryFields = computed<QueryField[]>(() => props.resource === 'audit' ? [{ key: 'requestMethod', label: '请求方式', options: dictionaries.options('sys_request_method') }, { key: 'operationType', label: '操作类型', options: dictionaries.options('sys_operation_type') }] : config.paged ? [] : [{ key: 'id', label: props.resource === 'roles' ? '角色 ID' : config.tree ? '节点 ID' : '编号', exact: true, numeric: true }, ...(props.resource === 'roles' ? [{ key: 'remark', label: '备注' }] : [])])
const details = ref<Row | null>(null)
const detailFields = computed(() => [...new Map([...config.columns, ...(config.fields ?? []).filter(field => !field.secret && field.type !== 'password'), { key: 'createdAt', label: '创建时间', kind: 'date' }, { key: 'updatedAt', label: '更新时间', kind: 'date' }, ...(props.resource === 'audit' ? [{ key: 'httpStatus', label: 'HTTP 状态' }, { key: 'responseCode', label: '业务状态' }, { key: 'requestParams', label: '查询参数' }, { key: 'requestBody', label: '请求体' }, { key: 'responseBody', label: '响应结果' }, { key: 'errorMessage', label: '错误信息' }] : [])].map(field => [field.key, field])).values()])
const rows = ref<Row[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const status = ref('')
const createdFrom = ref('')
const createdTo = ref('')
const applied = reactive({ keyword: '', status: '', createdFrom: '', createdTo: '' })
const pageSize = ref(20)
const collapsed = ref<string[]>([])
const deleting = ref<Row | null>(null)
const statusFilter = computed(() => {
  if (queryOptions[props.resource])
    return queryOptions[props.resource]
  const field = config.fields?.find(item => item.key === 'status' || item.key === 'enabled')
  if (!field)
    return undefined
  return { key: field.key, options: (field.dictionary ? dictionaries.options(field.dictionary) : field.options)?.map(item => ({ value: String(item.value), label: item.label })) ?? [{ value: 'true', label: '启用' }, { value: 'false', label: '停用' }] }
})
const searchHint = computed(() => ({ users: '账号或显示名称', audit: '操作名称或追踪号', history: '消息标题', refunds: '退款单号或原因', notifications: '业务订单号', menus: '菜单名称、路由或权限标识', roles: '请输入角色名称或编码' }[props.resource] ?? '名称、编码等关键词'))
const loading = ref(false)
const error = ref('')
const editor = ref(false)
const edited = ref<Row | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive<Record<string, unknown>>({})
const referencesReady = ref(false)
const options = reactive<Record<string, { value: string | number, label: string }[]>>({})
const storageType = ref('LOCAL')
const storageTypes = [
  { value: 'LOCAL', label: '本地磁盘' },
  { value: 'MINIO', label: 'MinIO' },
  { value: 'ALIYUN', label: '阿里云 OSS' },
]
const activeFields = computed(() =>
  (config.fields ?? []).filter(
    field => (!edited.value || !field.createOnly) && (!field.visible || field.visible(form)),
  ),
)
// 筛选条件全部交由服务端执行，前端只组织展示层级与分页。
const filteredRows = computed(() => config.grouped ? rows.value.filter(row => row.type === storageType.value) : rows.value)
const tree = computed(() => buildTree(filteredRows.value, props.resource === 'menus'))
const visibleTotal = computed(() => config.paged ? total.value : config.tree ? tree.value.flatMap(branchIds).length : filteredRows.value.length)

const tableRows = computed(() => {
  if (!config.tree)
    return (config.paged ? filteredRows.value : filteredRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value)).map(row => ({ key: String(row.id), row, label: '', depth: 0, children: false }))
  const flat: { key: string, row?: Row, label: string, depth: number, children: boolean }[] = []
  // 层级默认全部展开，只折叠用户明确操作的分支；查询时显示完整匹配路径。
  function append(nodes: TreeItem[], depth: number) {
    for (const node of nodes) {
      flat.push({ key: node.key, row: node.row, label: node.label, depth, children: node.children.length > 0 })
      if (applied.keyword || !collapsed.value.includes(node.key))
        append(node.children, depth + 1)
    }
  }
  append(tree.value, 0)
  return flat
})
/** 折叠操作只影响展示，不改变真实菜单父子关系。 */
function toggleNode(key: string) {
  collapsed.value = collapsed.value.includes(key) ? collapsed.value.filter(item => item !== key) : [...collapsed.value, key]
}
/** 一次收起所有分支，保留根节点作为导航。 */
function collapseAll() {
  const keys: string[] = []
  function collect(nodes: TreeItem[]) {
    for (const node of nodes) {
      if (node.children.length)
        keys.push(node.key)
      collect(node.children)
    }
  }
  collect(tree.value)
  collapsed.value = keys
}
/** 所有列表数据来源于真实接口，失败清空旧记录并提供重试。 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await request<Row[] | PageResult<Row> | Record<string, Row[]>>(
      config.endpoint,
      'GET',
      undefined,
      { ...props.fixedQuery, ...Object.fromEntries(Object.entries(appliedValues.value).filter(([, value]) => value.trim())), page: page.value, size: pageSize.value, keyword: applied.keyword, status: applied.status, ...(applied.createdFrom ? { createdFrom: applied.createdFrom } : {}), ...(applied.createdTo ? { createdTo: applied.createdTo } : {}) },
    )
    if (config.grouped) {
      rows.value = Object.values(result as Record<string, Row[]>).flat()
      total.value = rows.value.length
    }
    else if (Array.isArray(result)) {
      rows.value = result
      total.value = result.length
    }
    else {
      const data = result as PageResult<Row>
      rows.value = data.records
      total.value = data.total
    }
  }
  catch (failure) {
    rows.value = []
    total.value = 0
    error.value = failure instanceof Error ? failure.message : '加载失败'
  }
  finally {
    loading.value = false
  }
}
/** 引用字段获取实际可选对象，编辑时不把脱敏标志当成秘密提交。 */
async function open(row: Row | null) {
  edited.value = row
  formError.value = ''
  referencesReady.value = false
  Object.keys(form).forEach(key => delete form[key])
  for (const field of config.fields ?? []) {
    // 字典默认项用于新增表单，编辑始终回显当前记录的真实值。
    const defaultOption = field.dictionary ? dictionaries.options(field.dictionary).find(item => item.isDefault) : undefined
    const initial = defaultOption ? (field.options?.some(item => typeof item.value === 'number') ? Number(defaultOption.value) : defaultOption.value) : field.initial ?? null
    form[field.key] = field.secret ? '' : row ? (row[field.key] ?? null) : (props.fixedQuery?.[field.key] ?? initial)
  }
  editor.value = true
  saving.value = true
  try {
    await Promise.all(
      (config.fields ?? [])
        .filter(field => field.source)
        .map(async (field) => {
          const list = await request<Row[]>(field.source!)
          const excluded = new Set<number>()
          if (row && field.key === 'parentId') {
            const collect = (id: number) => {
              excluded.add(id)
              list.filter(item => item.parentId === id && !excluded.has(item.id)).forEach(item => collect(item.id))
            }
            collect(row.id)
          }
          options[field.key] = list
            .filter(item => field.key !== 'parentId' || (!excluded.has(item.id) && item.type !== 'BUTTON'))
            .map(item => ({
              value: field.sourceValue ? String(item[field.sourceValue]) : item.id,
              label: `${String(item.name ?? item.nickname ?? item.username)} (#${item.id})`,
            }))
        }),
    )
    referencesReady.value = true
  }
  catch (failure) {
    formError.value = failure instanceof Error ? failure.message : '选项加载失败'
  }
  finally {
    saving.value = false
  }
}
/** 按照字段定义转换输入类型并保留真实空值。 */
function setField(field: Field, value: unknown) {
// 保持编号与布尔字段的真实类型，缺失编号传 null，不用零或其他参数替代。
  if (value === '') {
    form[field.key] = field.type === 'select' || field.type === 'number' ? null : ''
    return
  }
  if (field.type === 'boolean') {
    form[field.key] = value === 'true'
  }
  else if (
    (field.source && !field.sourceValue)
    || field.type === 'number'
    || field.options?.some(option => typeof option.value === 'number')
  ) {
    form[field.key] = Number(value)
  }
  else {
    form[field.key] = String(value)
  }
}
/** 校验加载状态后提交明确的字段或授权集合。 */
async function save() {
// 引用目录未完整加载时禁止提交，避免旧选项或缺失选项覆盖现有归属。
  if (saving.value || !referencesReady.value)
    return
  saving.value = true
  formError.value = ''
  try {
    const payload: Record<string, unknown> = {}
    for (const field of config.fields ?? []) {
      if (edited.value && field.createOnly)
        continue
      payload[field.key] = field.visible && !field.visible(form) ? null : (form[field.key] ?? null)
    }
    await request(
      config.endpoint + (edited.value ? `/${edited.value.id}` : ''),
      edited.value ? 'PUT' : 'POST',
      payload,
    )
    editor.value = false
    toast.success('资料已保存')
    await load()
    // 字典数据变更后刷新共享选项，让其他页面立即使用实际配置。
    if (props.resource === 'dictionaries')
      window.dispatchEvent(new Event('sail:dictionaries-changed'))
    if (['menus', 'roles'].includes(props.resource))
      await auth.hydrate(true)
  }
  catch (failure) {
    formError.value = failure instanceof Error ? failure.message : '保存失败'
  }
  finally {
    saving.value = false
  }
}
/** 确认具体对象后调用后端删除接口。 */
async function remove(row: Row) {
  saving.value = true
  try {
    await request(`${config.endpoint}/${row.id}`, 'DELETE')
    toast.success('已删除')
    deleting.value = null
    await load()
    // 删除字典项后清除其他表单仍在使用的旧选项。
    if (props.resource === 'dictionaries')
      window.dispatchEvent(new Event('sail:dictionaries-changed'))
  }
  catch (failure) {
    toast.error(failure instanceof Error ? failure.message : '删除失败')
  }
  finally {
    saving.value = false
  }
}
/** 切换页码并按接口分页能力读取数据。 */
async function changePage(target: number) {
  page.value = target
  if (config.paged)
    await load()
}
/** 新搜索从第一页读取，避免沿用旧查询的页码。 */
async function search() {
  page.value = 1
  appliedValues.value = { ...queryValues.value }
  applied.keyword = keyword.value.trim()
  applied.status = status.value
  applied.createdFrom = createdFrom.value
  applied.createdTo = createdTo.value
  await load()
}
/** 清空查询条件后从第一页重新读取。 */
async function resetSearch() {
  queryValues.value = {}
  keyword.value = ''
  status.value = ''
  createdFrom.value = ''
  createdTo.value = ''
  await search()
}
/** 切换真实存储类型分组时重置该分组页码。 */
async function selectType(type: string) {
  storageType.value = type
  page.value = 1
}
/** 状态更新提交同一记录的真实可编辑字段，不携带脱敏凭据。 */
async function toggleStatus(row: Row, enabled: boolean) {
  if (saving.value || protectedRow(row))
    return
  const fields = (config.fields ?? []).filter(field => !field.createOnly)
  if (fields.some(field => field.secret || (field.required && (row[field.key] === null || row[field.key] === undefined)))) {
    toast.error('记录缺少完整编辑资料，请通过编辑表单修改状态')
    return
  }
  saving.value = true
  try {
    const payload = Object.fromEntries(fields.map(field => [field.key, field.key === 'status' ? Number(enabled) : row[field.key]]))
    await request(`${config.endpoint}/${row.id}`, 'PUT', payload)
    toast.success(enabled ? '已启用' : '已停用')
    await load()
    // 启停字典项同样改变可用选项，需要同步刷新共享目录。
    if (props.resource === 'dictionaries')
      window.dispatchEvent(new Event('sail:dictionaries-changed'))
    if (['roles', 'menus'].includes(props.resource))
      await auth.hydrate(true)
  }
  catch (failure) {
    toast.error(failure instanceof Error ? failure.message : '状态更新失败')
  }
  finally { saving.value = false }
}
// 标签失活时关闭传送到根节点的弹窗，避免遮挡其他标签。
onDeactivated(() => {
  editor.value = false
  deleting.value = null
  details.value = null
})
watch(() => props.fixedQuery, () => {
  page.value = 1
  void load()
}, { deep: true })
onMounted(load)
/** 展示标签取自配置，缺失字典时明确保留原值。 */
function renderValue(row: Row, field: { key: string, kind?: string, dictionary?: string }) {
  return field.dictionary ? dictionaries.label(field.dictionary, row[field.key]) : display(row[field.key], field.kind)
}
/** 表单字典保持后端原始的数字或字符串类型。 */
function fieldOptions(field: Field) {
  if (field.dictionary)
    return dictionaries.options(field.dictionary)
  return field.options ?? options[field.key] ?? []
}
/** 内置字典允许修改标签样式和备注，业务值保持稳定。 */
function immutableField(field: Field) {
  if (props.fixedQuery && field.key in props.fixedQuery)
    return true
  return Boolean(edited.value && (field.immutable || (props.resource === 'dictionaries' && edited.value.builtIn && field.key === 'value')))
}
</script>

<template>
  <section class="space-y-4">
    <AdvancedTable v-model:show-query="showQuery" v-model:hidden-columns="hiddenColumns" v-model:column-order="columnOrder" v-model:left-pins="leftPins" v-model:right-pins="rightPins" v-model:density="density" :columns="columns" :title="config.tree ? '目录结构' : `${config.title}列表`" :total="visibleTotal" :loading="loading" @refresh="load">
      <template #query>
        <QueryBar v-model:values="queryValues" v-model:created-from="createdFrom" v-model:created-to="createdTo" v-model:keyword="keyword" v-model:status="status" :dates="!config.tree" :fields="queryFields" :label="resource === 'roles' ? '角色名称' : '关键词'" :placeholder="searchHint" :statuses="statusFilter?.options" :busy="loading" @search="search" @reset="resetSearch" />
      </template>
      <template #actions>
        <template v-if="config.tree">
          <Button variant="outline" size="sm" @click="collapsed = []">
            <ChevronsUpDown class="size-3.5" />展开全部
          </Button><Button variant="outline" size="sm" @click="collapseAll">
            <ChevronsDownUp class="size-3.5" />收起全部
          </Button>
        </template>
        <Button v-if="canWrite" size="sm" :disabled="saving" @click="open(null)">
          <Plus class="size-3.5" />新增{{ resource === 'roles' ? '角色' : config.title.replace('管理', '').replace('配置', '') }}
        </Button>
      </template>
      <template #before>
        <div v-if="config.grouped" class="mb-3 flex flex-wrap gap-2" aria-label="按存储类型分类">
          <Button v-for="type in storageTypes" :key="type.value" size="sm" :variant="storageType === type.value ? 'default' : 'outline'" :aria-pressed="storageType === type.value" @click="selectType(type.value)">
            {{ type.label }}（{{ rows.filter(row => row.type === type.value).length }}）
          </Button>
        </div>
        <p v-if="error" role="alert" class="mb-3 rounded border border-destructive p-3 text-sm text-destructive">
          {{ error }} <Button variant="link" @click="load">
            重试
          </Button>
        </p>
      </template>
      <div v-if="loading" class="space-y-3 p-4" aria-label="正在加载">
        <Skeleton v-for="line in 5" :key="line" class="h-10 w-full" />
      </div>
      <Table v-else>
        <TableHeader>
          <TableRow>
            <TableHead v-for="column in visibleColumns" :key="column.key" :data-column="column.key">
              {{ column.label }}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="entry in tableRows" :key="entry.key" :class="{ 'bg-muted/20': !entry.row }">
            <TableCell v-for="column in visibleColumns" :key="column.key" :data-column="column.key" class="max-w-xs">
              <div v-if="column.key === '_actions' && entry.row" class="sail-row-actions">
                <Button v-if="canWrite && (!protectedRow(entry.row) || resource === 'dictionaries')" size="sm" variant="ghost" :disabled="saving" @click="open(entry.row)">
                  编辑
                </Button>
                <Button size="sm" variant="ghost" @click="details = entry.row">
                  详情
                </Button>
                <RolePermissions v-if="resource === 'roles' && auth.isAdmin" :row="entry.row" @changed="load" />
                <ResourceActions :resource="resource" :row="entry.row" @changed="load" />
                <Button v-if="canWrite && config.remove && !protectedRow(entry.row) && !(resource === 'users' && entry.row.id === 1)" size="sm" variant="ghost" class="text-destructive" :disabled="saving" @click="deleting = entry.row">
                  删除
                </Button>
              </div>
              <div v-else-if="config.tree && column.key === config.columns[0]?.key" class="flex min-w-48 items-center gap-1.5 text-left" :style="{ paddingLeft: `${entry.depth * 22}px` }">
                <button v-if="entry.children" class="flex size-6 shrink-0 items-center justify-center rounded hover:bg-muted" :aria-label="`${collapsed.includes(entry.key) ? '展开' : '收起'}${entry.label}`" :aria-expanded="!collapsed.includes(entry.key)" @click="toggleNode(entry.key)">
                  <component :is="collapsed.includes(entry.key) ? ChevronRight : ChevronDown" class="size-3.5" />
                </button><span v-else class="w-6 shrink-0" /><Folder v-if="entry.children" class="size-4 shrink-0 text-muted-foreground" /><span :class="{ 'font-medium': entry.children }">{{ entry.label }}</span>
              </div>
              <div v-else-if="entry.row && column.kind === 'state'" class="flex items-center justify-center gap-2">
                <Switch v-if="canWrite && !protectedRow(entry.row) && column.key === 'status' && config.fields?.some(field => field.key === 'status')" :model-value="entry.row.status === 1" :disabled="saving" :aria-label="`${entry.row.name ?? entry.row.username ?? entry.row.label}状态`" @update:model-value="toggleStatus(entry.row, $event)" /><Badge v-else variant="outline">
                  {{ display(entry.row[column.key], column.kind) }}
                </Badge><span v-if="canWrite && !protectedRow(entry.row) && column.key === 'status'" class="text-xs text-muted-foreground">{{ dictionaries.label('sys_status', entry.row.status) }}</span>
              </div>
              <Badge v-else-if="entry.row && resource === 'dictionaries' && column.key === 'label'" variant="outline" :data-tone="entry.row.color">
                {{ entry.row.label }}
              </Badge>
              <Badge v-else-if="entry.row && column.kind === 'boolean'" variant="outline">
                {{ display(entry.row[column.key], column.kind) }}
              </Badge>
              <Badge v-else-if="entry.row && column.dictionary" variant="outline" :data-tone="dictionaries.item(column.dictionary, entry.row[column.key])?.color">
                {{ dictionaries.label(column.dictionary, entry.row[column.key]) }}
              </Badge>
              <component :is="resolveMenuIcon(String(entry.row.icon ?? ''))" v-else-if="entry.row && column.key === 'icon' && resolveMenuIcon(String(entry.row.icon ?? ''))" class="mx-auto size-4" />
              <span v-else-if="entry.row && column.key === 'dataScope'">{{ ({ 1: '全部数据', 2: '自定义部门', 3: '本部门', 4: '本部门及下级', 5: '仅本人' } as Record<string, string>)[String(entry.row[column.key])] ?? display(entry.row[column.key]) }}</span>
              <span v-else-if="entry.row && column.key !== '_actions'" class="break-words" :class="{ 'font-mono text-xs text-muted-foreground': ['permission', 'code'].includes(column.key) }">{{ display(entry.row[column.key], column.kind) }}</span>
            </TableCell>
          </TableRow>
          <TableRow v-if="!tableRows.length">
            <TableCell :colspan="visibleColumns.length" class="h-40 text-muted-foreground">
              {{ error ? '加载失败，请刷新重试' : '暂无匹配记录，请调整查询条件或新增资料' }}
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
      <template v-if="!config.tree" #pagination>
        <ListPagination v-model:size="pageSize" :page="page" :total="visibleTotal" :busy="loading" @change="changePage" />
      </template>
    </AdvancedTable>
    <Dialog :open="Boolean(details)" @update:open="!$event && (details = null)">
      <DialogContent class="max-h-[85vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader><DialogTitle>记录详情</DialogTitle><DialogDescription>{{ config.title }} · #{{ details?.id }}</DialogDescription></DialogHeader><dl v-if="details" class="grid grid-cols-[100px_minmax(0,1fr)] gap-x-4 gap-y-3 text-sm">
          <template v-for="field in detailFields" :key="field.key">
            <dt class="text-muted-foreground">
              {{ field.label }}
            </dt><dd class="whitespace-pre-wrap break-all" :class="[['requestBody', 'responseBody', 'requestParams'].includes(field.key) ? 'max-h-64 overflow-auto rounded bg-muted p-3 font-mono text-xs' : '']">
              {{ renderValue(details, field as { key: string, kind?: string, dictionary?: string }) }}
            </dd>
          </template>
        </dl>
      </DialogContent>
    </Dialog>
    <Dialog :open="Boolean(deleting)" @update:open="!$event && (deleting = null)">
      <DialogContent>
        <DialogHeader><DialogTitle>删除{{ config.title.replace('管理', '') }}</DialogTitle><DialogDescription>确认删除「{{ deleting?.name ?? deleting?.username ?? deleting?.id }}」？</DialogDescription></DialogHeader><DialogFooter>
          <Button variant="outline" :disabled="saving" @click="deleting = null">
            取消
          </Button><Button variant="destructive" :disabled="saving" @click="deleting && remove(deleting)">
            {{ saving ? '正在删除…' : '确认删除' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    <Dialog v-model:open="editor">
      <DialogContent class="max-h-[85vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{{ edited ? '编辑' : '新增' }}{{ resource === 'roles' ? '角色' : config.title }}</DialogTitle>
          <DialogDescription>{{ activeFields.some(field => field.secret) ? '秘密字段编辑时留空表示保留原值。' : '带 * 的为必填项。' }}</DialogDescription>
        </DialogHeader>
        <SailForm class="space-y-4" @submit.prevent="save">
          <div v-for="field in activeFields" :key="field.key" class="space-y-2">
            <Label :for="`edit-${field.key}`">{{ field.label }}</Label>
            <div v-if="field.type === 'boolean'" class="flex h-8 items-center gap-2">
              <Switch :id="`edit-${field.key}`" :model-value="form[field.key] === true" :disabled="saving" @update:model-value="setField(field, String($event))" /><span class="text-sm text-muted-foreground">{{ field.key === 'isDefault' ? (form[field.key] ? '是' : '否') : (form[field.key] ? '启用' : '停用') }}</span>
            </div>
            <IconPicker v-else-if="field.type === 'icon'" :model-value="String(form[field.key] ?? '')" :disabled="saving" @update:model-value="setField(field, $event)" /><FormSelect v-else-if="field.type === 'select'" :id="`edit-${field.key}`" :model-value="String(form[field.key] ?? '')" :required="field.required" :disabled="saving || immutableField(field)" :placeholder="field.required ? '请选择' : '无'" :options="fieldOptions(field).map(item => ({ value: String(item.value), label: item.label }))" @update:model-value="setField(field, $event)" />
            <Textarea
              v-else-if="field.type === 'textarea'"
              :id="`edit-${field.key}`"
              :model-value="String(form[field.key] ?? '')"
              :required="field.required && !(edited && field.secret)"
              :maxlength="field.max"
              :disabled="saving"
              :autocomplete="field.secret ? 'off' : undefined"
              @update:model-value="(value) => setField(field, value)"
            />
            <Input
              v-else
              :id="`edit-${field.key}`"
              :model-value="String(form[field.key] ?? '')"
              :type="field.type ?? 'text'"
              :required="field.required && !(edited && field.secret)"
              :maxlength="field.max"
              :pattern="field.pattern"
              :minlength="field.type === 'password' ? field.min : undefined"
              :min="field.type === 'number' ? field.min : undefined"
              :disabled="saving || Boolean(edited && field.immutable)"
              :autocomplete="field.secret || field.type === 'password' ? 'new-password' : 'off'"
              @update:model-value="(value) => setField(field, value)"
            />
            <p v-if="field.help" class="text-xs text-muted-foreground">
              {{ field.help }}
            </p>
          </div>
          <p v-if="formError" role="alert" class="break-words text-sm text-destructive">
            {{ formError }}
          </p>
          <DialogFooter>
            <Button type="button" variant="outline" :disabled="saving" @click="editor = false">
              取消
            </Button>
            <Button type="submit" :disabled="saving || !referencesReady">
              {{ saving ? '正在保存…' : '保存资料' }}
            </Button>
          </DialogFooter>
        </SailForm>
      </DialogContent>
    </Dialog>
  </section>
</template>
