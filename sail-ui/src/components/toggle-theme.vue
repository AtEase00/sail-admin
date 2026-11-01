<script setup lang="ts">
import { MoonIcon, SunIcon, SunMoonIcon } from '@lucide/vue'
import { useColorMode } from '@vueuse/core'

import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'

const mode = useColorMode()
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label="切换主题"
        title="切换主题"
      >
        <SunIcon class="rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
        <MoonIcon class="absolute rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
        <span class="sr-only">切换主题</span>
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="end">
      <DropdownMenuItem @click="mode = 'light'">
        <SunIcon />
        浅色模式
      </DropdownMenuItem>
      <DropdownMenuItem @click="mode = 'dark'">
        <MoonIcon />
        深色模式
      </DropdownMenuItem>
      <DropdownMenuItem @click="mode = 'auto'">
        <SunMoonIcon />
        跟随系统
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
