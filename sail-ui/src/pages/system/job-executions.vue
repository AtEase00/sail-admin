<script setup lang="ts">
definePage({
  redirect: to => ({ path: '/system/jobs', query: { ...to.query, view: 'executions' } }),
})
</script>

<template>
  <span />
</template>
