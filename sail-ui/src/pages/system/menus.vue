<script setup lang="ts">
import ResourcePage from '@/sail/components/ResourcePage.vue'
</script>

<template>
  <ResourcePage resource="menus" />
</template>

<route lang="yaml">
meta:
  sail: true
  title: 菜单权限
  auth: true
  permission: system:menu:query
</route>
