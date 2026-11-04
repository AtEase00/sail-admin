<script setup lang="ts">
import { Building2, HardDrive, ImagePlus, Mail, RotateCcw, Save, ShieldCheck, Upload } from '@lucide/vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import ResourcePage from '@/sail/components/ResourcePage.vue'
import SailForm from '@/sail/components/SailForm.vue'
import { useAuthStore } from '@/stores/auth'

interface SettingField { key: string, label: string, group: string, inputType: string, help: string, value: string | null, min: number, max: number }
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const groups = [{ key: 'general', label: '基础信息', icon: Building2 }, { key: 'security', label: '登录安全', icon: ShieldCheck }, { key: 'storage', label: '文件存储', icon: HardDrive }, { key: 'message', label: '消息设置', icon: Mail }]
const active = computed(() => groups.some(item => item.key === route.query.tab) ? String(route.query.tab) : 'general')
const fields = ref<SettingField[]>([])
const values = reactive<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)
const error = useMessageError()
const logoUploading = ref(false)
const logoInput = ref<HTMLInputElement>()
/** 专用接口校验图片后上传，保存配置后统一更新侧栏和浏览器图标。 */
async function uploadLogo(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file)
    return
  if (file.size > 2097152) {
    error.value = '站点标志必须小于 2 MiB'
    return
  }
  logoUploading.value = true
  error.value = ''
  try {
    const form = new FormData()
    form.append('file', file)
    const result = await request<{ url: string }>('/file/website-logo', 'POST', form)
    values['website.logo'] = result.url
  }
  catch (failure) { error.value = errorMessage(failure, '标志上传失败') }
  finally { logoUploading.value = false }
}
const visible = computed(() => fields.value.filter(field => field.group === active.value))
const dirty = computed(() => visible.value.some(field => values[field.key] !== (field.value ?? '')))
/** 读取实际配置，缺失值保持空白并标明，不用示例值代替。 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    fields.value = await request<SettingField[]>('/system/configs/settings')
    fields.value.forEach((field) => {
      values[field.key] = field.value ?? ''
    })
  }
  catch (failure) { error.value = errorMessage(failure, '配置加载失败') }
  finally { loading.value = false }
}
/** 保存当前组的完整输入，后端在同一事务中校验和写入。 */
async function save() {
  if (saving.value || !auth.isAdmin)
    return
  saving.value = true
  error.value = ''
  try {
    const payload = Object.fromEntries(visible.value.map(field => [field.key, values[field.key]]))
    await request('/system/configs/settings', 'PUT', payload)
    // 只更新已保存组的基线，保留其他组尚未提交的输入。
    fields.value.forEach((field) => {
      if (field.key in payload)
        field.value = payload[field.key]!
    })
    window.dispatchEvent(new Event('sail:website-changed'))
    window.dispatchEvent(new Event('sail:message-settings-changed'))
    feedback.success('配置已保存并生效')
  }
  catch (failure) { error.value = errorMessage(failure, '保存失败') }
  finally { saving.value = false }
}
/** 切换组前保留未提交输入，返回时可继续编辑。 */
function selectGroup(key: string) {
  void router.replace({ query: { ...route.query, tab: key } })
}
/** 只还原当前组的未提交修改。 */
function reset() {
  visible.value.forEach((field) => {
    values[field.key] = field.value ?? ''
  })
}
onMounted(load)
</script>

<template>
  <section class="space-y-4">
    <nav class="flex flex-wrap gap-1 rounded-lg border bg-background p-1" aria-label="系统配置分组">
      <Button v-for="group in groups" :key="group.key" :variant="active === group.key ? 'secondary' : 'ghost'" class="h-10 px-5" :class="active === group.key ? 'bg-primary/10 text-primary' : 'text-muted-foreground'" :aria-current="active === group.key ? 'page' : undefined" @click="selectGroup(group.key)">
        <component :is="group.icon" class="size-4" />{{ group.label }}
      </Button>
    </nav>
    <ResourcePage v-if="active === 'storage'" resource="storages" />
    <div v-else class="rounded-lg border bg-background">
      <div class="flex items-center justify-between border-b px-6 py-4">
        <div>
          <h2 class="font-semibold">
            {{ active === 'general' ? '站点信息' : active === 'message' ? '消息发布与提醒' : '账号登录策略' }}
          </h2>
        </div><Button variant="ghost" size="icon" aria-label="重新加载配置" :disabled="loading || saving" @click="load">
          <RotateCcw class="size-4" />
        </Button>
      </div>

      <p v-if="loading" class="p-6 text-sm text-muted-foreground">
        正在读取配置…
      </p>
      <SailForm v-else class="max-w-3xl space-y-6 p-6" @submit.prevent="save">
        <div v-for="field in visible" :key="field.key" class="grid gap-2 sm:grid-cols-[120px_minmax(0,1fr)] sm:gap-5">
          <Label :for="field.key" class="sm:justify-end sm:pt-2">{{ field.label }}</Label>
          <div class="space-y-2">
            <div v-if="field.key === 'website.logo'" class="flex flex-wrap items-center gap-4">
              <div class="flex size-20 items-center justify-center rounded border bg-muted/20">
                <img v-if="values[field.key]" :src="values[field.key]" alt="站点标志预览" class="max-h-16 max-w-16 object-contain"><ImagePlus v-else class="size-7 text-muted-foreground stroke-1" />
              </div>
              <div class="space-y-2">
                <div class="flex gap-2">
                  <Button type="button" variant="outline" :disabled="saving || logoUploading || !auth.isAdmin" @click="logoInput?.click()">
                    <Upload class="size-4" />{{ logoUploading ? '正在上传…' : values[field.key] ? '更换图片' : '上传图片' }}
                  </Button><Button v-if="values[field.key]" type="button" variant="ghost" :disabled="saving || logoUploading || !auth.isAdmin" @click="values[field.key] = ''">
                    恢复默认
                  </Button>
                </div>
              </div>
              <input :ref="el => logoInput = el as HTMLInputElement" type="file" accept="image/png,image/jpeg,image/gif,image/webp,image/bmp" class="hidden" aria-label="上传站点标志" @change="uploadLogo">
            </div><Switch v-else-if="field.inputType === 'switch'" :id="field.key" :model-value="values[field.key] === '1'" :disabled="saving || !auth.isAdmin" @update:model-value="values[field.key] = $event ? '1' : '0'" /><Input v-else :id="field.key" v-model="values[field.key]" :type="field.inputType === 'number' ? 'number' : 'text'" :min="field.inputType === 'number' ? field.min : undefined" :max="field.inputType === 'number' ? field.max : undefined" :maxlength="field.key === 'website.title' ? 64 : 2000" :required="field.key !== 'website.logo'" :disabled="saving || !auth.isAdmin" />
          </div>
        </div>
        <div v-if="auth.isAdmin" class="flex gap-2 border-t pt-5 sm:pl-[140px]">
          <Button type="submit" :disabled="saving || logoUploading || !dirty">
            <Save class="size-4" />{{ saving ? '正在保存…' : '保存配置' }}
          </Button><Button type="button" variant="outline" :disabled="saving || logoUploading || !dirty" @click="reset">
            还原修改
          </Button>
        </div>
      </SailForm>
    </div>
  </section>
</template>

<route lang="yaml">
meta:
  sail: true
  title: 系统配置
  auth: true
  permission: system:config:query
</route>
