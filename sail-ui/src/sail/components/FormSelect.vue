<script setup lang="ts">
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

const props = withDefaults(defineProps<{ id?: string, ariaLabel?: string, modelValue?: string, options: { value: string, label: string }[], placeholder?: string, disabled?: boolean, required?: boolean, name?: string }>(), { placeholder: '请选择' })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
/** 空值使用独立选项标识，避免选择器保留值限制；业务层仍收到空字符串。 */
function change(value: unknown) {
  emit('update:modelValue', value === undefined || value === '__sail_empty__' ? '' : String(value))
}
</script>

<template>
  <Select :model-value="modelValue || (required ? undefined : '__sail_empty__')" :disabled="disabled" :required="required" :name="name" @update:model-value="change">
    <SelectTrigger :id="id" :aria-label="ariaLabel" :aria-required="required" class="w-full">
      <SelectValue :placeholder="placeholder">
        {{ options.find(option => option.value === modelValue)?.label ?? placeholder }}
      </SelectValue>
    </SelectTrigger>
    <SelectContent>
      <SelectItem v-if="!required" value="__sail_empty__">
        {{ props.placeholder }}
      </SelectItem><SelectItem v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </SelectItem><p v-if="!options.length" class="p-3 text-center text-xs text-muted-foreground">
        暂无可选项
      </p>
    </SelectContent>
  </Select>
</template>
