<script setup lang="ts">
import ResourcePage from '@/sail/components/ResourcePage.vue'
</script>

<template>
  <ResourcePage resource="depts" />
</template>

<route lang="yaml">
meta:
  sail: true
  title: 部门管理
  auth: true
  permission: system:dept:query
</route>
