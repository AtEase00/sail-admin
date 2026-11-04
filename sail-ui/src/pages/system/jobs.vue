<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import JobExecutions from '@/sail/components/JobExecutions.vue'
import JobList from '@/sail/components/JobList.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const activeTab = computed(() => auth.can('system:job:log:query') && (route.query.view === 'executions' || !auth.can('system:job:query')) ? 'executions' : 'jobs')
function selectTab(value: string | number) {
  void router.replace({ path: '/system/jobs', query: value === 'executions' ? { view: 'executions' } : {} })
}
function showRecords(jobId: number) {
  void router.replace({ path: '/system/jobs', query: { view: 'executions', jobId } })
}
</script>

<template>
  <section class="space-y-4">
    <Tabs :model-value="activeTab" @update:model-value="selectTab">
      <TabsList aria-label="定时任务视图">
        <TabsTrigger v-if="auth.can('system:job:query')" value="jobs">
          任务列表
        </TabsTrigger>
        <TabsTrigger v-if="auth.can('system:job:log:query')" value="executions">
          执行记录
        </TabsTrigger>
      </TabsList>
    </Tabs>
    <KeepAlive>
      <JobList v-if="activeTab === 'jobs' && auth.can('system:job:query')" @records="showRecords" />
      <JobExecutions v-else-if="auth.can('system:job:log:query')" />
    </KeepAlive>
  </section>
</template>

<route lang="yaml">
meta:
  sail: true
  auth: true
  title: 定时任务
  permissions: [system:job:query, system:job:log:query]
</route>
