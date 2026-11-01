import type { ListColumn } from '@/sail/list-models'
import type { Row } from '@/services/types/sail'

import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '@/sail/password-policy'
/** 可维护资料共用具名表单与表格，不把业务输入变为任意 JSON。 */
export interface Field {
  key: string
  label: string
  type?: 'text' | 'password' | 'textarea' | 'number' | 'boolean' | 'select' | 'icon'
  required?: boolean
  max?: number
  pattern?: string
  min?: number
  createOnly?: boolean
  immutable?: boolean
  initial?: string | number | boolean | null
  options?: { value: string | number | boolean, label: string }[]
  source?: string
  sourceValue?: string
  dictionary?: string
  secret?: boolean
  help?: string
  visible?: (form: Record<string, unknown>) => boolean
}
export interface Resource {
  title: string
  endpoint: string
  columns: ListColumn[]
  fields?: Field[]
  paged?: boolean
  remove?: boolean
  writePermission?: string
  adminWrite?: boolean
  grouped?: boolean
  keyword?: boolean
  tree?: boolean
}
const status: Field = {
  key: 'status',
  label: '状态',
  type: 'select',
  required: true,
  initial: 1,
  options: [
    { value: 1, label: '启用' },
    { value: 0, label: '停用' },
  ],
}
const enabled: Field = { key: 'enabled', label: '启用', type: 'boolean', initial: true }
const name: Field = { key: 'name', label: '名称', required: true, max: 64 }
const code: Field = { key: 'code', label: '编码', required: true, max: 64 }
const order: Field = {
  key: 'sortOrder',
  label: '排序号',
  type: 'number',
  initial: 0,
  required: true,
}
function parent(source: string): Field {
  return {
    key: 'parentId',
    label: '父节点',
    type: 'select',
    source,
    initial: null,
  }
}
const column = (key: string, label: string) => ({ key, label })
function organization(title: string, endpoint: string, fields: Field[]): Resource {
  return {
    title,
    endpoint,
    fields,
    remove: true,
    adminWrite: true,
    columns: [
      column('id', '编号'),
      column('name', '名称'),
      column('parentId', '父节点'),
      { key: 'status', label: '状态', kind: 'state' },
    ],
  }
}
const cloud = (form: Record<string, unknown>) => form.type !== 'LOCAL'
const wechat = (form: Record<string, unknown>) => form.type === 'WECHAT_NATIVE'
export const resources: Record<string, Resource> = {
  users: {
    title: '用户管理',
    endpoint: '/system/users',
    paged: true,
    keyword: true,
    remove: true,
    adminWrite: true,
    fields: [
      { key: 'username', label: '登录账号', required: true, max: 64, createOnly: true },
      {
        key: 'password',
        label: '初始密码',
        type: 'password',
        required: true,
        createOnly: true,
        min: PASSWORD_MIN_LENGTH,
        max: PASSWORD_MAX_LENGTH,
      },
      { key: 'nickname', label: '显示名称', required: true, max: 64 },
      { key: 'deptId', label: '所属部门', type: 'select', source: '/system/depts' },
      status,
    ],
    columns: [
      column('id', '编号'),
      column('username', '账号'),
      column('nickname', '名称'),
      column('deptId', '部门编号'),
      { key: 'status', label: '状态', kind: 'state' },
    ],
  },
  roles: {
    title: '角色授权',
    endpoint: '/system/roles',
    remove: true,
    adminWrite: true,
    fields: [
      { ...name, label: '角色名称' },
      { ...code, label: '角色编码', pattern: '[A-Z][A-Z0-9_]{1,63}', help: '2 至 64 位，以大写字母开头，可包含数字和下划线。' },
      status,
      { key: 'remark', label: '备注', type: 'textarea', max: 255 },
      {
        key: 'dataScope',
        label: '数据范围',
        type: 'select',
        required: true,
        initial: 5,
        options: [
          { value: 1, label: '全部数据' },
          { value: 2, label: '自定义部门' },
          { value: 3, label: '本部门' },
          { value: 4, label: '本部门及下级' },
          { value: 5, label: '仅本人' },
        ],
      },
    ],
    columns: [
      column('name', '角色名称'),
      column('id', '角色 ID'),
      column('code', '角色编码'),
      { key: 'status', label: '状态', kind: 'state' },
      column('dataScope', '数据范围'),
      column('remark', '备注'),
      { key: 'createdAt', label: '创建时间', kind: 'date' },
    ],
  },
  menus: organization('菜单权限', '/system/menus', [
    name,
    parent('/system/menus'),
    {
      key: 'type',
      label: '节点类型',
      required: true,
      type: 'select',
      initial: 'MENU',
      options: [
        { value: 'DIRECTORY', label: '目录' },
        { value: 'MENU', label: '菜单' },
        { value: 'BUTTON', label: '按钮' },
      ],
    },
    { key: 'permission', label: '权限标识', max: 128, help: '按钮必填，例如 system:user:query。' },
    { key: 'path', label: '前端路由', max: 200 },
    { key: 'icon', label: '菜单图标', type: 'icon', max: 64, visible: form => form.type !== 'BUTTON' },
    order,
    status,
  ]),
  depts: organization('部门管理', '/system/depts', [name, parent('/system/depts'), order, status]),
  posts: {
    title: '岗位管理',
    endpoint: '/system/posts',
    adminWrite: true,
    remove: true,
    fields: [name, code, status],
    columns: [
      column('id', '编号'),
      column('name', '岗位'),
      column('code', '编码'),
      { key: 'status', label: '状态', kind: 'state' },
    ],
  },
  dictionaries: {
    title: '数据字典',
    endpoint: '/system/dictionaries',
    adminWrite: true,
    remove: true,
    fields: [
      { key: 'typeCode', label: '字典类型', type: 'select', source: '/system/dictionaries/types', sourceValue: 'code', required: true, immutable: true },
      { key: 'label', label: '展示名称', required: true, max: 100 },
      { key: 'value', label: '业务值', required: true, max: 100 },
      { key: 'color', label: '标签样式', type: 'select', initial: 'default', options: [{ value: 'default', label: '默认' }, { value: 'success', label: '成功' }, { value: 'processing', label: '进行中' }, { value: 'warning', label: '警告' }, { value: 'error', label: '错误' }] },
      { key: 'isDefault', label: '默认项', type: 'boolean', initial: false },
      { key: 'remark', label: '备注', type: 'textarea', max: 255 },
      order,
      status,
    ],
    columns: [
      column('id', '编号'),
      column('typeCode', '类型'),
      column('label', '名称'),
      column('value', '业务值'),
      column('sortOrder', '排序'),
      { key: 'isDefault', label: '默认项', kind: 'boolean' },
      { key: 'status', label: '状态', kind: 'state' },
    ],
  },
  configs: {
    title: '系统参数',
    endpoint: '/system/configs',
    adminWrite: true,
    fields: [
      {
        key: 'configKey',
        label: '参数键',
        type: 'select',
        required: true,
        options: ['website.title', 'website.logo', 'login.max_attempts', 'login.lock_minutes'].map(
          value => ({ value, label: value }),
        ),
      },
      { key: 'configValue', label: '参数值', required: true, max: 2000 },
      { key: 'description', label: '说明', max: 255 },
    ],
    columns: [
      column('id', '编号'),
      column('configKey', '参数键'),
      column('configValue', '参数值'),
      column('description', '说明'),
    ],
  },
  audit: {
    title: '操作审计',
    endpoint: '/system/audit',
    paged: true,
    columns: [
      column('id', '编号'),
      column('userId', '操作者'),
      column('action', '操作'),
      { key: 'operationType', label: '操作类型', dictionary: 'sys_operation_type' },
      { key: 'requestMethod', label: '请求方式', dictionary: 'sys_request_method' },
      column('requestUrl', '请求地址'),
      column('durationMs', '耗时（ms）'),
      { key: 'success', label: '结果', dictionary: 'sys_result' },
      column('traceId', '追踪号'),
      { key: 'createdAt', label: '时间', kind: 'date' },
    ],
  },
  templates: {
    title: '消息模板',
    endpoint: '/messages/templates',
    writePermission: 'message:template:write',
    fields: [
      code,
      name,
      { key: 'title', label: '标题模板', required: true, max: 200 },
      {
        key: 'content',
        label: '正文模板',
        type: 'textarea',
        required: true,
        max: 4000,
        help: '变量写法：' + '$' + '{name}。发送时必须提供全部变量。',
      },
      enabled,
    ],
    columns: [
      column('id', '编号'),
      column('code', '编码'),
      column('name', '名称'),
      column('title', '标题'),
      { key: 'enabled', label: '启用', kind: 'boolean' },
    ],
  },
  history: {
    title: '消息发送记录',
    endpoint: '/messages/history',
    paged: true,
    columns: [
      column('id', '编号'),
      column('recipientId', '接收人'),
      column('senderId', '发送人'),
      column('title', '标题'),
      { key: 'readFlag', label: '阅读状态', dictionary: 'message_read_status' },
      column('createdAt', '时间'),
    ],
  },
  storages: {
    title: '存储配置',
    endpoint: '/file/storages',
    grouped: true,
    adminWrite: true,
    remove: true,
    fields: [
      name,
      {
        key: 'type',
        label: '存储类型',
        type: 'select',
        required: true,
        initial: 'LOCAL',
        options: [
          { value: 'LOCAL', label: '本地磁盘' },
          { value: 'MINIO', label: 'MinIO' },
          { value: 'ALIYUN', label: '阿里云 OSS' },
        ],
      },
      { key: 'endpoint', label: '服务 endpoint', required: true, visible: cloud, max: 255 },
      { key: 'bucket', label: '存储桶 bucket', required: true, visible: cloud, max: 100 },
      {
        key: 'region',
        label: '区域 region',
        visible: cloud,
        max: 80,
        help: 'MinIO 必须填写实际 region。',
      },
      {
        key: 'accessKey',
        label: '访问标识 Access Key',
        secret: true,
        visible: cloud,
        type: 'password',
        required: true,
        max: 256,
      },
      {
        key: 'secretKey',
        label: '访问密钥 Secret Key',
        secret: true,
        visible: cloud,
        type: 'password',
        required: true,
        max: 512,
      },
      enabled,
    ],
    columns: [
      column('id', '编号'),
      column('name', '名称'),
      column('type', '分类'),
      column('endpoint', '服务地址'),
      column('bucket', '存储桶'),
      { key: 'selected', label: '默认', kind: 'boolean' },
      { key: 'enabled', label: '启用', kind: 'boolean' },
    ],
  },
  applications: {
    title: '支付应用',
    endpoint: '/pay/applications',
    adminWrite: true,
    fields: [name, { ...code, immutable: true }, enabled],
    columns: [
      column('id', '编号'),
      column('name', '名称'),
      column('code', '编码'),
      { key: 'enabled', label: '启用', kind: 'boolean' },
    ],
  },
  channels: {
    title: '支付渠道',
    endpoint: '/pay/channels',
    adminWrite: true,
    fields: [
      name,
      {
        key: 'applicationId',
        label: '业务应用',
        type: 'select',
        source: '/pay/applications',
        required: true,
      },
      {
        key: 'type',
        label: '渠道类型',
        type: 'select',
        required: true,
        initial: 'WECHAT_NATIVE',
        options: [
          { value: 'WECHAT_NATIVE', label: '微信 Native' },
          { value: 'ALIPAY_PRECREATE', label: '支付宝当面付' },
        ],
      },
      { key: 'appId', label: '渠道 App ID', required: true, max: 64 },
      { key: 'merchantId', label: '微信商户号 / 支付宝 seller_id', required: true, max: 64 },
      {
        key: 'merchantSerial',
        label: '微信商户证书序列号',
        required: true,
        visible: wechat,
        max: 100,
      },
      {
        key: 'publicKeyId',
        label: '微信支付平台公钥 ID',
        required: true,
        visible: wechat,
        max: 100,
      },
      {
        key: 'privateKey',
        label: '商户私钥 PEM',
        type: 'textarea',
        secret: true,
        required: true,
        max: 10000,
      },
      {
        key: 'publicKey',
        label: '支付平台公钥 PEM',
        type: 'textarea',
        secret: true,
        required: true,
        max: 10000,
      },
      {
        key: 'apiV3Key',
        label: '微信 APIv3 密钥（32 字节）',
        type: 'password',
        secret: true,
        required: true,
        visible: wechat,
        max: 64,
      },
      { key: 'notifyBaseUrl', label: '公网 HTTPS 回调基础地址', required: true, max: 200 },
      enabled,
    ],
    columns: [
      column('id', '编号'),
      column('name', '渠道'),
      column('type', '类型'),
      column('applicationId', '应用'),
      column('appId', 'App ID'),
      column('merchantId', '商户'),
      { key: 'enabled', label: '启用', kind: 'boolean' },
    ],
  },
  refunds: {
    title: '退款记录',
    endpoint: '/pay/refunds',
    paged: true,
    columns: [
      column('id', '编号'),
      column('orderId', '订单编号'),
      column('refundNo', '退款单号'),
      { key: 'amount', label: '退款金额（元）', kind: 'money' },
      column('status', '状态'),
      column('reason', '原因'),
      column('lastError', '最近错误'),
    ],
  },
  notifications: {
    title: '支付业务通知',
    endpoint: '/pay/notifications',
    paged: true,
    columns: [
      column('id', '事件编号'),
      column('businessNo', '业务订单'),
      column('eventType', '事件'),
      column('status', '状态'),
      column('attempts', '调用次数'),
      { key: 'nextAttemptAt', label: '下次投递', kind: 'date' },
      column('lastError', '诊断'),
    ],
  },
}
/** 树形页面保持完整层级，不截断父子节点。 */
resources.menus!.tree = true
resources.depts!.tree = true
resources.menus!.columns = [column('name', '菜单名称'), { key: 'type', label: '类型', dictionary: 'sys_menu_type' }, column('icon', '图标'), column('permission', '权限标识'), column('path', '路由地址'), column('sortOrder', '排序'), { key: 'status', label: '状态', kind: 'state' }]
resources.depts!.columns = [column('name', '部门名称'), column('id', '编号'), column('sortOrder', '排序'), { key: 'status', label: '状态', kind: 'state' }]

/** 字典配置与业务字段显式关联，状态原值不参与转换。 */
for (const [resource, key, dictionary] of [
  ['roles', 'dataScope', 'sys_data_scope'],
  ['storages', 'type', 'file_storage_type'],
  ['refunds', 'status', 'pay_refund_status'],
  ['menus', 'type', 'sys_menu_type'],
]) {
  const field = resources[resource!]?.fields?.find(item => item.key === key)
  if (field)
    field.dictionary = dictionary
  const column = resources[resource!]?.columns.find(item => item.key === key)
  if (column)
    column.dictionary = dictionary
}
/** 显示业务枚举含义，不替换接口实际状态。 */
export const stateLabels: Record<string, string> = {
  DIRECTORY: '目录',
  MENU: '菜单',
  BUTTON: '按钮',
  LOCAL: '本地磁盘',
  MINIO: 'MinIO',
  ALIYUN: '阿里云 OSS',
  WECHAT_NATIVE: '微信扫码',
  ALIPAY_PRECREATE: '支付宝扫码',
  CREATED: '待处理',
  PRECREATED: '待支付',
  PAID: '已支付',
  CLOSED: '已关闭',
  FAILED: '失败',
  UNKNOWN: '待确认',
  PROCESSING: '处理中',
  SUCCESS: '成功',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  PENDING: '待支付',
  WAITING: '待投递',
  DELIVERED: '已送达',
  DEAD: '已停止',
}
/** 状态选项与接口使用相同的字段值，中文仅参与展示。 */
export const queryOptions: Record<string, { key: string, options: { value: string, label: string }[] }> = {
  users: { key: 'status', options: [{ value: '1', label: '启用' }, { value: '0', label: '停用' }] },
  audit: { key: 'success', options: [{ value: 'true', label: '成功' }, { value: 'false', label: '失败' }] },
  history: { key: 'readFlag', options: [{ value: 'false', label: '未读' }, { value: 'true', label: '已读' }] },
  refunds: { key: 'status', options: ['PROCESSING', 'SUCCESS'].map(value => ({ value, label: stateLabels[value] ?? value })) },
  notifications: { key: 'status', options: ['WAITING', 'DELIVERED'].map(value => ({ value, label: stateLabels[value] ?? value })) },
}

/** 格式化只处理明确的显示类型，空值统一显示未提供。 */
export function display(value: unknown, kind?: string): string {
  if (value === null || value === undefined || value === '')
    return '—'
  if (kind === 'date')
    return String(value).replace('T', ' ').slice(0, 19)
  if (kind === 'boolean')
    return value ? '是' : '否'
  if (kind === 'state')
    return value === 1 ? '启用' : '停用'
  if (kind === 'money' && typeof value === 'number')
    return (value / 100).toFixed(2)
  return stateLabels[String(value)] ?? String(value)
}
/** 识别受后端内置标记保护的目录记录。 */
export function protectedRow(row: Row) {
  return row.builtIn === true
}
