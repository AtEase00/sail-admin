<script setup lang="ts">
import { Plus } from '@lucide/vue'
import { Input as AInput, Switch as ASwitch, Modal } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'

import type { MessageTemplate } from '@/sail/message-center'
import type { PageResult } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { request } from '@/lib/api-client'
import AdvancedTable from '@/sail/components/AdvancedTable.vue'
import ListPagination from '@/sail/components/ListPagination.vue'
import MessageContentView from '@/sail/components/MessageContentView.vue'
import MessageEditor from '@/sail/components/MessageEditor.vue'
import MessageTheme from '@/sail/components/MessageTheme.vue'
import QueryBar from '@/sail/components/QueryBar.vue'
import { templateVariables } from '@/sail/message-center'
import { useListView } from '@/sail/use-list-view'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const columns = [{ key: 'name', label: '模板名称' }, { key: 'code', label: '模板编码' }, { key: 'title', label: '消息标题' }, { key: 'variables', label: '模板变量' }, { key: 'enabled', label: '状态' }, { key: 'createdAt', label: '创建时间' }]
const { showQuery, hiddenColumns, columnOrder, leftPins, rightPins, density, visibleColumns } = useListView('message-templates', columns)
const rows = ref<MessageTemplate[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const status = ref('')
const createdFrom = ref('')
const createdTo = ref('')
const applied = ref<Record<string, string>>({})
const busy = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const editOpen = ref(false)
const editing = ref<number>()
const form = reactive({ code: '', name: '', title: '', content: '', enabled: true })
const previewOpen = ref(false)
const previewTemplate = ref<MessageTemplate>()
const values = reactive<Record<string, string>>({})
const rendered = ref<{ title: string, content: string }>()
let revision = 0
const variables = computed(() => templateVariables(`${form.title} ${form.content}`))
const previewVariables = computed(() => previewTemplate.value ? templateVariables(`${previewTemplate.value.title} ${previewTemplate.value.content}`) : [])
/** 关键字、启用状态与创建时间在同一 SQL 条件中分页。 */
async function load() {
  const current = ++revision
  busy.value = true
  error.value = ''
  try {
    const result = await request<PageResult<MessageTemplate>>('/messages/templates/page', 'GET', undefined, { ...applied.value, page: page.value, size: size.value })
    if (current !== revision)
      return
    rows.value = result.records
    total.value = result.total
  }
  catch (failure) {
    if (current === revision)
      error.value = failure instanceof Error ? failure.message : '模板读取失败'
  }
  finally {
    if (current === revision)
      busy.value = false
  }
}
/** 应用当前筛选并从第一页查询，避免在浏览器过滤分页结果。 */
function search() {
  applied.value = { keyword: keyword.value.trim(), status: status.value, ...(createdFrom.value ? { createdFrom: createdFrom.value } : {}), ...(createdTo.value ? { createdTo: createdTo.value } : {}) }
  page.value = 1
  void load()
}
/** 清空查询字段后重新向后端获取列表。 */
function reset() {
  keyword.value = ''
  status.value = ''
  createdFrom.value = ''
  createdTo.value = ''
  search()
}
/** 编辑器只维护模板文本与变量，发布时再生成独立内容快照。 */
function edit(row?: MessageTemplate) {
  editing.value = row?.id
  Object.assign(form, row ? { code: row.code, name: row.name, title: row.title, content: row.content, enabled: row.enabled } : { code: '', name: '', title: '', content: '', enabled: true })
  formError.value = ''
  editOpen.value = true
}
/** 校验模板必填内容并提交真实新增或更新接口。 */
async function save() {
  if (saving.value)
    return
  if (!form.code.trim() || !form.name.trim() || !form.title.trim() || !form.content.replace(/<[^>]*>/g, '').trim()) {
    formError.value = '请填写编码、名称、标题与正文'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    await request(editing.value ? `/messages/templates/${editing.value}` : '/messages/templates', editing.value ? 'PUT' : 'POST', { ...form })
    editOpen.value = false
    toast.success('模板已保存')
    await load()
  }
  catch (failure) {
    formError.value = failure instanceof Error ? failure.message : '模板保存失败'
  }
  finally {
    saving.value = false
  }
}
/** 删除前明确确认对象，历史消息继续保留发布快照。 */
function remove(row: MessageTemplate) {
  Modal.confirm({ title: '删除消息模板？', content: `模板“${row.name}”删除后无法再用于新消息，已发消息不受影响。`, okText: '删除模板', cancelText: '取消', okButtonProps: { danger: true }, onOk: async () => {
    try {
      await request(`/messages/templates/${row.id}`, 'DELETE')
      await load()
      toast.success('模板已删除')
    }
    catch (failure) {
      error.value = failure instanceof Error ? failure.message : '删除失败'
      throw failure
    }
  } })
}
/** 打开变量填写预览，缺少变量时等待用户输入。 */
function showPreview(row: MessageTemplate) {
  previewTemplate.value = row
  rendered.value = undefined
  previewVariables.value.forEach((key) => {
    values[key] = ''
  })
  previewOpen.value = true
  formError.value = ''
  if (!previewVariables.value.length)
    void render()
}
/** 变量预览使用真实模板服务渲染，缺失输入不会被示例值替代。 */
async function render() {
  if (!previewTemplate.value)
    return
  saving.value = true
  formError.value = ''
  try {
    rendered.value = await request(`/messages/templates/${previewTemplate.value.id}/preview`, 'POST', { variables: Object.fromEntries(previewVariables.value.map(key => [key, values[key] ?? ''])) })
  }
  catch (failure) {
    formError.value = failure instanceof Error ? failure.message : '模板预览失败'
  }
  finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<template>
  <MessageTheme>
    <AdvancedTable v-model:show-query="showQuery" v-model:hidden-columns="hiddenColumns" v-model:column-order="columnOrder" v-model:left-pins="leftPins" v-model:right-pins="rightPins" v-model:density="density" :columns="columns" title="消息模板" :total="total" :loading="busy" @refresh="load">
      <template #query>
        <QueryBar v-model:keyword="keyword" v-model:status="status" v-model:created-from="createdFrom" v-model:created-to="createdTo" dates label="模板名称" placeholder="名称、编码或消息标题" :statuses="[{ value: 'true', label: '已启用' }, { value: 'false', label: '已停用' }]" :busy="busy" @search="search" @reset="reset" />
      </template><template #actions>
        <Button v-if="auth.can('message:template:write')" size="sm" @click="edit()">
          <Plus class="size-4" />新增模板
        </Button>
      </template><template #before>
        <p v-if="error" role="alert" class="mb-3 text-sm text-destructive">
          {{ error }}
        </p>
      </template><div class="overflow-auto">
        <table class="w-full min-w-[850px] text-left text-sm">
          <thead class="bg-muted/40">
            <tr>
              <th v-for="column in visibleColumns" :key="column.key" :data-column="column.key" class="px-4 py-3 font-medium whitespace-nowrap">
                {{ column.label }}
              </th><th data-column="actions" class="px-4 py-3 font-medium">
                操作
              </th>
            </tr>
          </thead><tbody>
            <tr v-for="row in rows" :key="row.id" class="border-t hover:bg-muted/20">
              <td v-for="column in visibleColumns" :key="column.key" :data-column="column.key" class="max-w-64 truncate px-4 py-3">
                <span v-if="column.key === 'enabled'" :class="row.enabled ? 'text-primary' : 'text-muted-foreground'">{{ row.enabled ? '已启用' : '已停用' }}</span><span v-else-if="column.key === 'variables'" class="text-xs text-muted-foreground">{{ templateVariables(`${row.title} ${row.content}`).join('、') || '无变量' }}</span><span v-else>{{ row[column.key as 'name' | 'code' | 'title' | 'createdAt'] }}</span>
              </td><td data-column="actions" class="px-4 py-3">
                <div class="flex gap-3 whitespace-nowrap">
                  <Button variant="link" size="sm" class="h-auto p-0" @click="showPreview(row)">
                    预览
                  </Button><Button v-if="auth.can('message:template:write')" variant="link" size="sm" class="h-auto p-0" @click="edit(row)">
                    编辑
                  </Button><Button v-if="auth.can('message:template:delete')" variant="link" size="sm" class="h-auto p-0 text-destructive" @click="remove(row)">
                    删除
                  </Button>
                </div>
              </td>
            </tr><tr v-if="!rows.length">
              <td :colspan="visibleColumns.length + 1" class="p-12 text-center text-muted-foreground">
                {{ busy ? '正在读取模板…' : '暂无符合条件的消息模板' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div><template #pagination>
        <ListPagination v-model:size="size" :page="page" :total="total" :busy="busy" @change="page = $event; load()" />
      </template>
    </AdvancedTable>
    <Modal v-model:open="editOpen" :mask-closable="false" :title="editing ? '编辑消息模板' : '新增消息模板'" :width="860" :z-index="60" ok-text="保存模板" cancel-text="取消" :confirm-loading="saving" @ok="save">
      <div class="space-y-5 py-4">
        <div class="grid gap-4 sm:grid-cols-2">
          <label class="space-y-2"><span class="block text-sm">模板名称 <span class="text-destructive">*</span></span><AInput v-model:value="form.name" :maxlength="100" placeholder="便于选择的模板名称" /></label><label class="space-y-2"><span class="block text-sm">模板编码 <span class="text-destructive">*</span></span><AInput v-model:value="form.code" :maxlength="64" placeholder="英文开头，可包含数字、下划线" /></label>
        </div><label class="block space-y-2"><span class="block text-sm">消息标题 <span class="text-destructive">*</span></span><AInput v-model:value="form.title" :maxlength="200" placeholder="支持 ${变量名}" /></label><div class="space-y-2">
          <span class="text-sm">模板正文 <span class="text-destructive">*</span></span><MessageEditor v-model="form.content" placeholder="编写正文，使用 ${name} 声明变量" />
        </div><div class="flex flex-wrap items-center justify-between gap-3">
          <span class="text-xs text-muted-foreground">已识别变量：{{ variables.join('、') || '无' }}。变量只作文本替换，不执行表达式。</span><span class="flex items-center gap-2 text-sm">启用模板<ASwitch v-model:checked="form.enabled" aria-label="启用模板" /></span>
        </div><p v-if="formError" role="alert" class="text-sm text-destructive">
          {{ formError }}
        </p>
      </div>
    </Modal>
    <Modal v-model:open="previewOpen" :mask-closable="false" title="模板预览" :width="760" :z-index="60" :footer="null">
      <div class="space-y-4 py-3">
        <form v-if="previewVariables.length" class="space-y-3 border-b pb-4" @submit.prevent="render">
          <div class="grid gap-3 sm:grid-cols-2">
            <label v-for="name in previewVariables" :key="name" class="space-y-2"><span class="block text-sm">{{ name }}</span><AInput v-model:value="values[name]" :maxlength="1000" :placeholder="`填写 ${name}`" /></label>
          </div><Button type="submit" size="sm" :disabled="saving">
            生成预览
          </Button>
        </form><p v-if="formError" role="alert" class="text-sm text-destructive">
          {{ formError }}
        </p><template v-if="rendered">
          <h2 class="text-xl font-semibold break-words">
            {{ rendered.title }}
          </h2><MessageContentView :content="rendered.content" />
        </template>
      </div>
    </Modal>
  </MessageTheme>
</template>

<route lang="yaml">
meta:
  sail: true
  title: 消息模板
  auth: true
  permission: message:template:query
</route>
