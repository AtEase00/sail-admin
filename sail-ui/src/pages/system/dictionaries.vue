<script setup lang="ts">
import { BookOpen, Pencil, Plus, Search, Trash2 } from '@lucide/vue'
import { onMounted, reactive, ref } from 'vue'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import ResourcePage from '@/sail/components/ResourcePage.vue'
import SailForm from '@/sail/components/SailForm.vue'
import { useAuthStore } from '@/stores/auth'

interface DictionaryType { id: number, name: string, code: string, status: number, builtIn: boolean, remark: string }
const auth = useAuthStore()
const types = ref<DictionaryType[]>([])
const selected = ref<DictionaryType | null>(null)
const keyword = ref('')
const error = useMessageError()
const loading = ref(false)
const editor = ref(false)
const edited = ref<DictionaryType | null>(null)
const deleting = ref<DictionaryType | null>(null)
const saving = ref(false)
const formError = useMessageError()
const form = reactive({ name: '', code: '', status: 1, remark: '' })
/** 类型搜索交给后端过滤，不在客户端隐藏全部响应数据。 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    types.value = await request<DictionaryType[]>('/system/dictionaries/types', 'GET', undefined, { keyword: keyword.value.trim() })
    selected.value = types.value.find(item => item.id === selected.value?.id) ?? types.value[0] ?? null
  }
  catch (failure) {
    error.value = errorMessage(failure, '类型加载失败')
    types.value = []
    selected.value = null
  }
  finally { loading.value = false }
}
/** 编辑时保留真实类型编码，系统类型仅允许调整展示和状态。 */
function open(type: DictionaryType | null) {
  edited.value = type
  formError.value = ''
  Object.assign(form, type ? { name: type.name, code: type.code, status: type.status, remark: type.remark } : { name: '', code: '', status: 1, remark: '' })
  editor.value = true
}
/** 新类型可直接进入数据维护，编码修改由后端事务迁移。 */
async function save() {
  saving.value = true
  formError.value = ''
  try {
    const id = await request<number>(`/system/dictionaries/types${edited.value ? `/${edited.value.id}` : ''}`, edited.value ? 'PUT' : 'POST', { ...form })
    editor.value = false
    await load()
    selected.value = types.value.find(item => item.id === id) ?? null
    window.dispatchEvent(new Event('sail:dictionaries-changed'))
    feedback.success('字典类型已保存')
  }
  catch (failure) { formError.value = errorMessage(failure, '保存失败') }
  finally { saving.value = false }
}
/** 删除前由服务端检查系统保护和字典数据引用。 */
async function remove() {
  if (!deleting.value)
    return
  saving.value = true
  formError.value = ''
  try {
    await request(`/system/dictionaries/types/${deleting.value.id}`, 'DELETE')
    deleting.value = null
    await load()
    window.dispatchEvent(new Event('sail:dictionaries-changed'))
    feedback.success('字典类型已删除')
  }
  catch (failure) { formError.value = errorMessage(failure, '删除失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <section class="grid items-start gap-4 xl:grid-cols-[280px_minmax(0,1fr)]">
    <aside class="overflow-hidden rounded-lg border bg-background">
      <div class="flex h-14 items-center justify-between border-b px-4">
        <span class="flex items-center gap-2 font-medium"><BookOpen class="size-4 text-primary" />字典类型</span><Button v-if="auth.isAdmin" variant="ghost" size="icon" class="size-8" aria-label="新增字典类型" @click="open(null)">
          <Plus class="size-4" />
        </Button>
      </div>
      <form class="flex gap-2 p-3" role="search" @submit.prevent="load">
        <Input v-model="keyword" placeholder="名称或编码" aria-label="搜索字典类型" /><Button type="submit" variant="outline" size="icon" class="size-8 shrink-0" :disabled="loading" aria-label="查询字典类型">
          <Search class="size-4" />
        </Button>
      </form>

      <p v-if="loading" class="p-4 text-sm text-muted-foreground">
        正在读取类型…
      </p>
      <div v-else class="max-h-56 xl:max-h-[calc(100vh-280px)] overflow-y-auto p-2 pt-0">
        <div v-for="type in types" :key="type.id" class="group flex min-h-16 items-center rounded-md px-3" :class="selected?.id === type.id ? 'bg-primary/10 text-primary' : 'hover:bg-muted'">
          <button type="button" class="min-w-0 flex-1 py-2 text-left" :aria-pressed="selected?.id === type.id" @click="selected = type">
            <span class="block truncate text-sm font-medium">{{ type.name }}<span v-if="type.status === 0" class="ml-2 text-xs text-muted-foreground">停用</span></span><span class="mt-1 block truncate font-mono text-xs text-muted-foreground">{{ type.code }}</span>
          </button>
          <div v-if="auth.isAdmin" class="flex shrink-0">
            <Button variant="ghost" size="icon" class="size-7" :aria-label="`编辑${type.name}`" @click="open(type)">
              <Pencil class="size-3.5" />
            </Button><Button v-if="!type.builtIn" variant="ghost" size="icon" class="size-7 text-destructive" :aria-label="`删除${type.name}`" @click="deleting = type; formError = ''">
              <Trash2 class="size-3.5" />
            </Button>
          </div>
        </div>
        <p v-if="!types.length" class="p-5 text-center text-sm text-muted-foreground">
          没有匹配的字典类型
        </p>
      </div>
    </aside>
    <div class="min-w-0">
      <div v-if="selected" class="mb-3 flex items-baseline gap-3 px-1">
        <h2 class="text-base font-semibold">
          {{ selected.name }}
        </h2><span class="text-xs text-muted-foreground">{{ selected.code }}</span><span v-if="selected.builtIn" class="ml-auto text-xs text-muted-foreground">系统字典 · 业务值受保护</span>
      </div><ResourcePage v-if="selected" :key="selected.id" resource="dictionaries" :fixed-query="{ typeCode: selected.code }" /><p v-else class="rounded-lg border bg-background p-12 text-center text-sm text-muted-foreground">
        请选择或新增字典类型
      </p>
    </div>
    <Dialog v-model:open="editor">
      <DialogContent :aria-describedby="undefined" class="sm:max-w-lg">
        <DialogHeader><DialogTitle>{{ edited ? '编辑字典类型' : '新增字典类型' }}</DialogTitle></DialogHeader><SailForm class="space-y-4" @submit.prevent="save">
          <div class="space-y-2">
            <Label for="type-name">类型名称</Label><Input id="type-name" v-model="form.name" required :maxlength="64" :disabled="saving" />
          </div><div class="space-y-2">
            <Label for="type-code">类型编码</Label><Input id="type-code" v-model="form.code" pattern="[a-z][a-z0-9_]{1,63}" required :disabled="saving || edited?.builtIn" placeholder="例如 business_status" />
          </div><div class="flex items-center gap-3">
            <Label for="type-status">启用类型</Label><Switch id="type-status" :model-value="form.status === 1" :disabled="saving" @update:model-value="form.status = Number($event)" />
          </div><div class="space-y-2">
            <Label for="type-remark">备注</Label><Textarea id="type-remark" v-model="form.remark" :maxlength="255" :disabled="saving" />
          </div><DialogFooter>
            <Button type="button" variant="outline" :disabled="saving" @click="editor = false">
              取消
            </Button><Button type="submit" :disabled="saving">
              {{ saving ? '正在保存…' : '保存类型' }}
            </Button>
          </DialogFooter>
        </SailForm>
      </DialogContent>
    </Dialog>
    <Dialog :open="Boolean(deleting)" @update:open="!$event && (deleting = null)">
      <DialogContent>
        <DialogHeader><DialogTitle>删除字典类型</DialogTitle><DialogDescription>确认删除「{{ deleting?.name }}」？有数据的类型需要先清空数据。</DialogDescription></DialogHeader><DialogFooter>
          <Button variant="outline" :disabled="saving" @click="deleting = null">
            取消
          </Button><Button variant="destructive" :disabled="saving" @click="remove">
            确认删除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </section>
</template>

<route lang="yaml">
meta:
  sail: true
  title: 数据字典
  auth: true
  permission: system:dictionary:query
</route>
