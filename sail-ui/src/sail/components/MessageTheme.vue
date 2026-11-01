<script setup lang="ts">
import { useDark } from '@vueuse/core'
import { ConfigProvider, theme } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

import { useThemeStore } from '@/stores/theme'

const dark = useDark()
const settings = useThemeStore()
</script>

<template>
  <ConfigProvider :locale="zhCN" :theme="{ algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm, token: { colorPrimary: settings.brandColor, borderRadius: 4, fontSize: 13, controlHeight: 32, zIndexPopupBase: 60 }, components: { Drawer: { zIndexPopup: 60 }, DatePicker: { zIndexPopup: 70 }, Select: { zIndexPopup: 70 } } }">
    <slot />
  </ConfigProvider>
</template>
