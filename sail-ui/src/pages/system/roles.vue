<script setup lang="ts">
import ResourcePage from '@/sail/components/ResourcePage.vue'
</script>

<template>
  <ResourcePage resource="roles" />
</template>

<route lang="yaml">
meta:
  sail: true
  title: 角色授权
  auth: true
  permission: system:role:query
</route>
