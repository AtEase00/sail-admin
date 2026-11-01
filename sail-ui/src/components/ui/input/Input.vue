<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { useVModel } from '@vueuse/core'
import { Eye, EyeOff } from '@lucide/vue'
import { computed, ref, useAttrs } from 'vue'
import { cn } from '@/lib/utils'

defineOptions({ inheritAttrs: false })
const props = withDefaults(defineProps<{
  defaultValue?: string | number
  modelValue?: string | number
  type?: string
  class?: HTMLAttributes['class']
}>(), { type: 'text' })
const emits = defineEmits<{ (e: 'update:modelValue', payload: string | number): void }>()
const attrs = useAttrs()
const visible = ref(false)
const modelValue = useVModel(props, 'modelValue', emits, { passive: true, defaultValue: props.defaultValue })
const inputClass = computed(() => cn('file:text-foreground placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground border-input h-8 w-full min-w-0 rounded-md border bg-background px-3 py-1 text-sm outline-none disabled:cursor-not-allowed disabled:opacity-50', props.class))
</script>

<template>
  <div v-if="type === 'password'" class="relative min-w-0 w-full">
    <input v-bind="attrs" v-model="modelValue" data-slot="input" :type="visible ? 'text' : 'password'" :class="cn(inputClass, 'pr-9')">
    <button type="button" :disabled="attrs.disabled !== undefined && attrs.disabled !== false" :aria-label="visible ? '隐藏密码' : '显示密码'" :aria-pressed="visible" class="absolute top-1/2 right-2 -translate-y-1/2 rounded p-1 text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/30" @click="visible = !visible"><component :is="visible ? EyeOff : Eye" class="size-4" /></button>
  </div>
  <input v-else v-bind="attrs" v-model="modelValue" data-slot="input" :type="type" :class="inputClass">
</template>
