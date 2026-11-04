<script setup lang="ts">
import QRCode from 'qrcode'
import { computed, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'

import type { PageResult, PayOrder, Row } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import AdvancedTable from '@/sail/components/AdvancedTable.vue'
import FormSelect from '@/sail/components/FormSelect.vue'
import ListPagination from '@/sail/components/ListPagination.vue'
import QueryBar from '@/sail/components/QueryBar.vue'
import SailForm from '@/sail/components/SailForm.vue'
import { useListView } from '@/sail/use-list-view'
import { useAuthStore } from '@/stores/auth'
import { useDictionaryStore } from '@/stores/dictionaries'

const auth = useAuthStore()
const dictionaries = useDictionaryStore()
const orders = ref<PayOrder[]>([])
const columns = [{ key: 'businessNo', label: '业务订单 / 商户单号' }, { key: 'subject', label: '标题' }, { key: 'amount', label: '金额（元）' }, { key: 'status', label: '状态' }, { key: 'expiresAt', label: '到期时间' }, { key: '_actions', label: '操作' }]
const { showQuery, hiddenColumns, columnOrder, leftPins, rightPins, density, visibleColumns } = useListView('orders', columns)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const status = ref('')
const createdFrom = ref('')
const createdTo = ref('')
const appliedQuery = ref({ keyword: '', status: '', createdFrom: '', createdTo: '' })
/** 查询条件应用于整套数据，从第一页重新读取。 */
async function search() {
  appliedQuery.value = { keyword: keyword.value.trim(), status: status.value, createdFrom: createdFrom.value, createdTo: createdTo.value }
  page.value = 1
  await load()
}
/** 重置检索条件并同步结果。 */
async function resetSearch() {
  keyword.value = ''
  status.value = ''
  createdFrom.value = ''
  createdTo.value = ''
  await search()
}

const total = ref(0)
const loading = ref(false)
const operating = ref(false)
const busy = computed(() => loading.value || operating.value)
const error = useMessageError()
const creating = ref(false)
const confirmNew = ref(false)
const closeTarget = ref<PayOrder | null>(null)
const formError = useMessageError()
const applications = ref<Row[]>([])
const channels = ref<Row[]>([])
const createKey = ref('')
const draft = reactive({
  applicationId: '',
  channelId: '',
  businessNo: '',
  subject: '',
  yuan: '',
  expiresMinutes: 15,
})
const available = computed(() =>
  channels.value.filter(channel => String(channel.applicationId) === draft.applicationId),
)
const viewed = ref<PayOrder | null>(null)
const qrImage = ref('')
const detailOpen = ref(false)
const detailError = useMessageError()
const refundOpen = ref(false)
const refundTarget = ref<PayOrder | null>(null)
const reason = ref('')
const refundError = useMessageError()
let poll: number | undefined
let detailVersion = 0
let disposed = false
const configurationReady = ref(false)
/** 读取当前页面的真实数据并维护加载与错误状态。 */
async function load() {
  if (disposed)
    return
  loading.value = true
  error.value = ''
  try {
    const result = await request<PageResult<PayOrder>>('/pay/orders', 'GET', undefined, {
      page: page.value,
      size: pageSize.value,
      keyword: appliedQuery.value.keyword,
      status: appliedQuery.value.status,
      ...(appliedQuery.value.createdFrom ? { createdFrom: appliedQuery.value.createdFrom } : {}),
      ...(appliedQuery.value.createdTo ? { createdTo: appliedQuery.value.createdTo } : {}),
    })
    orders.value = result.records
    total.value = result.total
  }
  catch (failure) {
    orders.value = []
    total.value = 0
    error.value = errorMessage(failure, '订单加载失败')
  }
  finally {
    loading.value = false
  }
}
/** 读取真实启用应用和渠道后允许创建订单。 */
async function openCreate() {
  if (busy.value)
    return
  creating.value = true
  formError.value = ''
  operating.value = true
  configurationReady.value = false
  try {
    const [apps, methods] = await Promise.all([
      request<Row[]>('/pay/applications'),
      request<Row[]>('/pay/channels/available'),
    ])
    applications.value = apps.filter(app => app.enabled)
    channels.value = methods
    configurationReady.value = true
  }
  catch (failure) {
    formError.value = errorMessage(failure, '支付配置加载失败')
  }
  finally {
    operating.value = false
  }
}
/** 金额文本按整数分精确解析，不用浮点乘法隐式舍入。 */
function amountInCents(value: string) {
  if (!/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(value))
    throw new Error('金额必须为最多两位小数的人民币元')
  const [whole, fraction = ''] = value.split('.')
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
  if (!Number.isSafeInteger(cents) || cents < 1 || cents > 100000000)
    throw new Error('金额范围为 0.01 至 1000000.00 元')
  return cents
}
/** 展示当前订单与渠道二维码并开始本地状态补读。 */
async function show(order: PayOrder) {
  if (disposed)
    return
  stopDetail()
  const version = detailVersion
  viewed.value = order
  qrImage.value = ''
  detailError.value = ''
  detailOpen.value = true
  try {
    if (order.qrCode && order.status === 'PENDING') {
      const rendered = await QRCode.toDataURL(order.qrCode, {
        width: 288,
        margin: 2,
        errorCorrectionLevel: 'M',
      })
      if (version === detailVersion)
        qrImage.value = rendered
    }
  }
  catch {
    if (version === detailVersion)
      detailError.value = '二维码绘制失败，可点击重新获取二维码重试。'
  }
  // 等上次读取完成再安排下一次，切换订单或关闭弹窗后旧结果不能回写。
  /** 顺序补读当前订单，关闭详情后禁止回写旧结果。 */
  async function refreshDetail() {
    if (version !== detailVersion || !detailOpen.value || viewed.value?.status !== 'PENDING')
      return
    try {
      const current = await request<PayOrder>(`/pay/orders/${order.id}`)
      if (version !== detailVersion || !detailOpen.value)
        return
      viewed.value = current
      if (current.status !== 'PENDING') {
        qrImage.value = ''
        await load()
        return
      }
      poll = window.setTimeout(refreshDetail, 5000)
    }
    catch (failure) {
      if (version === detailVersion)
        detailError.value = errorMessage(failure, '状态读取失败，请手动查单')
    }
  }
  if (version === detailVersion && detailOpen.value && order.status === 'PENDING')
    poll = window.setTimeout(refreshDetail, 5000)
}
/** 关闭详情或切换订单时同时失效异步结果与待执行轮询。 */
function stopDetail() {
  detailVersion++
  window.clearTimeout(poll)
  qrImage.value = ''
}
/** 保留真实业务单号并以整数分提交订单。 */
async function create(confirmed = false) {
  if (busy.value || !configurationReady.value)
    return
  formError.value = ''
  operating.value = true
  try {
    const payload = {
      applicationId: Number(draft.applicationId),
      channelId: Number(draft.channelId),
      businessNo: draft.businessNo,
      subject: draft.subject,
      amount: amountInCents(draft.yuan),
      expiresMinutes: Number(draft.expiresMinutes),
    }
    // 首次超时后保留原业务单号，重试仍由后端唯一键核对交易参数。
    const key = `${payload.applicationId}:${payload.businessNo}`
    if (createKey.value && createKey.value !== key && !confirmed) {
      confirmNew.value = true
      return
    }
    confirmNew.value = false
    createKey.value = key
    const order = await request<PayOrder>('/pay/orders', 'POST', payload)
    creating.value = false
    createKey.value = ''
    await load()
    await show(order)
  }
  catch (failure) {
    formError.value
      = `${errorMessage(failure, '下单失败')
      }。如发生网络失败，请保留业务单号并核对订单列表，不要另建单号重复扣款。`
  }
  finally {
    operating.value = false
  }
}
/** 执行渠道查单、原单重试或确认关单操作。 */
async function operate(order: PayOrder, action: 'sync' | 'resubmit' | 'close') {
  if (busy.value)
    return
  // 显式渠道操作期间暂停该单的旧轮询，避免旧状态覆盖操作结果。
  if (viewed.value?.id === order.id)
    stopDetail()
  operating.value = true
  error.value = ''
  detailError.value = ''
  try {
    await request(`/pay/orders/${order.id}/${action}`, 'POST')
    closeTarget.value = null
    const current = await request<PayOrder>(`/pay/orders/${order.id}`)
    await load()
    if (detailOpen.value && viewed.value?.id === order.id)
      await show(current)
    feedback.success('操作已完成')
  }
  catch (failure) {
    const message = errorMessage(failure, '操作失败')
    error.value = message
    detailError.value = message
  }
  finally {
    operating.value = false
  }
}
/** 展示实际订单的全额退款确认信息。 */
function openRefund(order: PayOrder) {
  refundTarget.value = order
  reason.value = ''
  refundError.value = ''
  refundOpen.value = true
}
/** 提交整单退款并展示后端真实处理状态。 */
async function refund() {
  if (!refundTarget.value || busy.value)
    return
  operating.value = true
  refundError.value = ''
  try {
    const result = await request<Row>(`/pay/orders/${refundTarget.value.id}/refund`, 'POST', {
      reason: reason.value,
    })
    feedback.success(
      result.status === 'SUCCESS' ? '整单退款成功' : '退款申请已提交',
    )
    refundOpen.value = false
    await load()
  }
  catch (failure) {
    refundError.value
      = errorMessage(failure, '退款申请失败，请核对退款记录后重试')
  }
  finally {
    operating.value = false
  }
}
/** 切换页码并按接口分页能力读取数据。 */
async function changePage(target: number) {
  page.value = target
  await load()
}
onDeactivated(() => {
  creating.value = false
  confirmNew.value = false
  closeTarget.value = null
  detailOpen.value = false
  refundOpen.value = false
  stopDetail()
})
onMounted(load)
watch(
  detailOpen,
  (value) => {
    if (!value)
      stopDetail()
  },
  { flush: 'sync' },
)
onBeforeUnmount(() => {
  disposed = true
  stopDetail()
})
</script>

<template>
  <section class="space-y-5">
    <AdvancedTable v-model:show-query="showQuery" v-model:hidden-columns="hiddenColumns" v-model:column-order="columnOrder" v-model:left-pins="leftPins" v-model:right-pins="rightPins" v-model:density="density" :columns="columns" title="支付订单" :total="total" :loading="busy" @refresh="load">
      <template #query>
        <QueryBar v-model:created-from="createdFrom" v-model:created-to="createdTo" v-model:keyword="keyword" v-model:status="status" dates placeholder="业务订单号、商户单号或标题" :statuses="dictionaries.options('pay_order_status')" :busy="busy" @search="search" @reset="resetSearch" />
      </template>
      <template #actions>
        <Button v-if="auth.can('pay:create')" size="sm" :disabled="busy" @click="openCreate">
          创建扫码订单
        </Button>
      </template>
      <template #before>
        <p v-if="busy" role="status" class="text-sm text-muted-foreground">
          处理中…
        </p>
      </template>
      <Table>
        <TableHeader class="bg-muted/40">
          <TableRow>
            <TableHead v-for="column in visibleColumns" :key="column.key" :data-column="column.key">
              {{ column.label }}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="order in orders" :key="order.id">
            <TableCell v-for="column in visibleColumns" :key="column.key" :data-column="column.key" class="max-w-xs whitespace-normal break-words">
              <template v-if="column.key === 'businessNo'">
                <p>{{ order.businessNo }}</p>
                <p class="text-xs text-muted-foreground">
                  {{ order.orderNo }}
                </p>
              </template>
              <template v-else-if="column.key === 'subject'">
                {{ order.subject }}
              </template>
              <template v-else-if="column.key === 'amount'">
                {{ (order.amount / 100).toFixed(2) }}
              </template>
              <template v-else-if="column.key === 'status'">
                {{ dictionaries.label('pay_order_status', order.status) }}
              </template>
              <template v-else-if="column.key === 'expiresAt'">
                {{ order.expiresAt }}
              </template>
              <template v-else-if="column.key === '_actions'">
                <div class="sail-row-actions">
                  <Button size="sm" variant="ghost" :disabled="busy" @click="show(order)">
                    详情
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    :disabled="busy"
                    @click="operate(order, 'sync')"
                  >
                    查单
                  </Button>
                  <Button
                    v-if="order.status === 'PENDING' && auth.can('pay:manage')"
                    size="sm"
                    variant="ghost"
                    :disabled="busy"
                    @click="closeTarget = order"
                  >
                    关单
                  </Button>
                  <Button
                    v-if="order.status === 'PAID' && auth.can('pay:refund')"
                    size="sm"
                    variant="destructive"
                    :disabled="busy"
                    @click="openRefund(order)"
                  >
                    整单退款
                  </Button>
                </div>
              </template>
            </TableCell>
          </TableRow>
          <TableRow v-if="!orders.length && !busy">
            <TableCell :colspan="visibleColumns.length" class="h-24 text-center text-muted-foreground">
              {{ error ? '订单加载失败，请重试。' : '暂无订单' }}
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
      <template #pagination>
        <ListPagination v-model:size="pageSize" :page="page" :total="total" :busy="busy" @change="changePage" />
      </template>
    </AdvancedTable>
    <Dialog v-model:open="confirmNew">
      <DialogContent>
        <DialogHeader><DialogTitle>开始另一笔交易</DialogTitle><DialogDescription>上一笔提交可能已经落库，请先核对订单列表。确认使用新的业务单号发起交易？</DialogDescription></DialogHeader><DialogFooter>
          <Button variant="outline" @click="confirmNew = false">
            返回核对
          </Button><Button :disabled="busy" @click="create(true)">
            确认创建新订单
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    <Dialog :open="Boolean(closeTarget)" @update:open="!$event && (closeTarget = null)">
      <DialogContent>
        <DialogHeader><DialogTitle>关闭未支付订单</DialogTitle><DialogDescription>确认关闭订单「{{ closeTarget?.businessNo }}」？付款二维码将不再可用。</DialogDescription></DialogHeader><DialogFooter>
          <Button variant="outline" :disabled="busy" @click="closeTarget = null">
            取消
          </Button><Button variant="destructive" :disabled="busy" @click="closeTarget && operate(closeTarget, 'close')">
            确认关单
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    <Dialog v-model:open="creating">
      <DialogContent :aria-describedby="undefined" class="max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>创建扫码支付订单</DialogTitle>
        </DialogHeader>
        <SailForm class="space-y-4" @submit.prevent="create()">
          <div class="space-y-2">
            <Label for="pay-app">业务应用</Label>
            <FormSelect id="pay-app" v-model="draft.applicationId" :options="applications.map(app => ({ value: String(app.id), label: String(app.name) }))" required :disabled="busy" @update:model-value="draft.channelId = ''" />
          </div>
          <div class="space-y-2">
            <Label for="pay-channel">支付渠道</Label>
            <FormSelect id="pay-channel" v-model="draft.channelId" :options="available.map(channel => ({ value: String(channel.id), label: String(channel.name) }))" placeholder="请选择可用渠道" required :disabled="busy" />
          </div>
          <div class="space-y-2">
            <Label for="business-no">真实业务订单号</Label>
            <Input
              id="business-no"
              v-model="draft.businessNo"
              pattern="[a-zA-Z0-9_-]{1,64}"
              required
              maxlength="64"
              :disabled="busy"
            />
          </div>
          <div class="space-y-2">
            <Label for="subject">交易标题</Label>
            <Input id="subject" v-model="draft.subject" required maxlength="120" :disabled="busy" />
          </div>
          <div class="space-y-2">
            <Label for="amount-yuan">应付金额（人民币元）</Label>
            <Input
              id="amount-yuan"
              v-model="draft.yuan"
              inputmode="decimal"
              required
              :disabled="busy"
              placeholder="填写业务订单的真实金额"
            />
          </div>
          <div class="space-y-2">
            <Label for="expires">有效期（分钟）</Label>
            <Input
              id="expires"
              v-model="draft.expiresMinutes"
              type="number"
              min="1"
              max="120"
              required
              :disabled="busy"
            />
          </div>

          <DialogFooter>
            <Button variant="outline" type="button" :disabled="busy" @click="creating = false">
              取消
            </Button>
            <Button type="submit" :disabled="busy || !configurationReady">
              {{ busy ? '正在下单…' : '生成付款二维码' }}
            </Button>
          </DialogFooter>
        </SailForm>
      </DialogContent>
    </Dialog>
    <Dialog v-model:open="detailOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>订单 {{ viewed?.businessNo }}</DialogTitle>
          <DialogDescription>{{ viewed ? dictionaries.label('pay_order_status', viewed.status) : '' }}</DialogDescription>
        </DialogHeader>
        <div v-if="viewed" class="space-y-4">
          <p class="text-center text-xl font-semibold tabular-nums">
            ¥ {{ (viewed.amount / 100).toFixed(2) }}
          </p>
          <img
            v-if="qrImage && viewed.status === 'PENDING'"
            :src="qrImage"
            alt="此订单的支付二维码，请使用对应支付渠道扫码"
            width="288"
            height="288"
            class="mx-auto max-w-full rounded-md"
          >
          <p v-if="viewed.status === 'PENDING'" class="text-center text-sm text-muted-foreground">
            付款截止 {{ viewed.expiresAt }}
          </p>
          <p v-if="!viewed.qrCode && viewed.status === 'PENDING'" class="text-sm">
            暂无付款二维码
          </p>

          <div class="flex flex-wrap justify-center gap-2">
            <Button variant="outline" :disabled="busy" @click="operate(viewed, 'sync')">
              主动查单
            </Button>
            <Button
              v-if="viewed.status === 'PENDING' && auth.can('pay:create')"
              :disabled="busy"
              @click="operate(viewed, 'resubmit')"
            >
              重新获取二维码
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
    <Dialog v-model:open="refundOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>整单退款</DialogTitle>
          <DialogDescription>
            将申请退回订单 {{ refundTarget?.businessNo }} 的全部金额 ¥{{
              refundTarget ? (refundTarget.amount / 100).toFixed(2) : ''
            }}。
          </DialogDescription>
        </DialogHeader>
        <SailForm class="space-y-4" @submit.prevent="refund">
          <Label for="refund-reason">退款原因</Label>
          <Input id="refund-reason" v-model="reason" required maxlength="100" :disabled="busy" />

          <DialogFooter>
            <Button type="button" variant="outline" :disabled="busy" @click="refundOpen = false">
              取消
            </Button>
            <Button type="submit" variant="destructive" :disabled="busy">
              {{ busy ? '申请中…' : '确认申请整单退款' }}
            </Button>
          </DialogFooter>
        </SailForm>
      </DialogContent>
    </Dialog>
  </section>
</template>

<route lang="yaml">
meta:
  sail: true
  auth: true
  permission: pay:query
  title: 支付订单
</route>
