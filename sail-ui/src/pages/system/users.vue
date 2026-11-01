<script setup lang="ts">
import ResourcePage from '@/sail/components/ResourcePage.vue'
</script>

<template>
  <ResourcePage resource="users" />
</template>

<route lang="yaml">
meta:
  sail: true
  title: 用户管理
  auth: true
  permission: system:user:query
</route>
