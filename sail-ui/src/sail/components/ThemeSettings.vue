<script setup lang="ts">
import { Check, Monitor, Moon, Palette, Sun } from '@lucide/vue'
import { useColorMode } from '@vueuse/core'
import { ref, watch } from 'vue'

import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useMessageError } from '@/composables/use-message-error'
import { BRAND_COLORS, DEFAULT_BRAND_COLOR } from '@/sail/theme-palette'
import { useThemeStore } from '@/stores/theme'

const theme = useThemeStore()
const mode = useColorMode({ emitAuto: true })
const input = ref(theme.brandColor)
const error = useMessageError()
const modes = [{ value: 'light', name: '浅色', icon: Sun }, { value: 'dark', name: '深色', icon: Moon }, { value: 'auto', name: '跟随系统', icon: Monitor }] as const
watch(() => theme.brandColor, (value) => {
  input.value = value
})
/** 颜色选择器和手动色值使用同一校验入口，立即更新并持久化。 */
function applyColor(value: string) {
  error.value = theme.setBrandColor(value.trim()) ? '' : '请输入 # 开头的 6 位十六进制色值'
}
</script>

<template>
  <Popover>
    <PopoverTrigger as-child>
      <Button variant="ghost" size="icon" class="size-8" aria-label="主题设置">
        <Palette class="size-4" />
      </Button>
    </PopoverTrigger>
    <PopoverContent align="end" class="w-80 space-y-5 p-5">
      <div class="flex items-center justify-between">
        <h2 class="text-sm font-semibold">
          主题设置
        </h2><button type="button" class="text-xs text-primary" @click="applyColor(DEFAULT_BRAND_COLOR); mode = 'auto'">
          恢复默认
        </button>
      </div>
      <section class="space-y-3" aria-label="外观模式">
        <p class="text-xs text-muted-foreground">
          外观
        </p>
        <div class="flex gap-2">
          <button v-for="item in modes" :key="item.value" type="button" class="flex flex-1 flex-col items-center gap-2 rounded border px-2 py-3 text-xs" :class="mode === item.value ? 'border-primary bg-accent text-accent-foreground' : 'hover:bg-muted'" :aria-pressed="mode === item.value" @click="mode = item.value">
            <component :is="item.icon" class="size-4" />{{ item.name }}
          </button>
        </div>
      </section>
      <section class="space-y-3" aria-label="主题颜色">
        <p class="text-xs text-muted-foreground">
          主题色
        </p>
        <div class="flex justify-between">
          <button v-for="color in BRAND_COLORS" :key="color.value" type="button" class="flex size-7 items-center justify-center rounded text-white ring-offset-2 focus-visible:ring-2 focus-visible:ring-ring" :style="{ backgroundColor: color.value }" :aria-label="`${color.name}主题`" :aria-pressed="theme.brandColor === color.value" :title="color.name" @click="applyColor(color.value)">
            <Check v-if="theme.brandColor === color.value" class="size-4" />
          </button>
        </div>
        <div class="flex items-center gap-2">
          <input type="color" :value="theme.brandColor" aria-label="选择自定义主题色" class="h-8 w-10 cursor-pointer rounded border bg-background p-1" @input="applyColor(($event.target as HTMLInputElement).value)"><input v-model="input" aria-label="主题色色值" class="h-8 min-w-0 flex-1 rounded border bg-background px-2 font-mono text-sm outline-none focus:border-primary focus:ring-2 focus:ring-ring/15" maxlength="7" @change="applyColor(input)" @keydown.enter.prevent="applyColor(input)">
        </div>
      </section>
    </PopoverContent>
  </Popover>
</template>
