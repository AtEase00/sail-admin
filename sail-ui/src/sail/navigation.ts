/** 固定页面能力清单；展示名称优先使用后端已授权菜单的相同路径名称。 */
export const navigation = [
  { title: '工作台', path: '/dashboard', group: '工作空间' },
  { title: '用户管理', path: '/system/users', permission: 'system:user:query', group: '系统管理' },
  { title: '角色授权', path: '/system/roles', permission: 'system:role:query', group: '系统管理' },
  { title: '菜单权限', path: '/system/menus', permission: 'system:menu:query', group: '系统管理' },
  { title: '部门管理', path: '/system/depts', permission: 'system:dept:query', group: '系统管理' },
  { title: '岗位管理', path: '/system/posts', permission: 'system:post:query', group: '系统管理' },
  {
    title: '数据字典',
    path: '/system/dictionaries',
    permission: 'system:dictionary:query',
    group: '系统管理',
  },
  {
    title: '系统配置',
    path: '/system/configs',
    permission: 'system:config:query',
    group: '系统管理',
  },
  { title: '定时任务', path: '/system/jobs', permissions: ['system:job:query', 'system:job:log:query'], group: '系统管理' },
  { title: '操作审计', path: '/system/audit', permission: 'system:audit:query', group: '系统管理' },
  { title: '文件管理', path: '/files', permission: 'file:query', group: '文件管理' },
  {
    title: '消息模板',
    path: '/messages/templates',
    permission: 'message:template:query',
    group: '消息管理',
  },
  {
    title: '发布记录',
    path: '/messages/history',
    permissions: ['message:query', 'message:send'],
    group: '消息管理',
  },
  { title: '支付订单', path: '/pay/orders', permission: 'pay:query', group: '支付管理' },
  { title: '支付应用', path: '/pay/applications', permission: 'pay:query', group: '支付管理' },
  { title: '支付渠道', path: '/pay/channels', admin: true, group: '支付管理' },
  { title: '退款记录', path: '/pay/refunds', permission: 'pay:manage', group: '支付管理' },
  { title: '业务通知', path: '/pay/notifications', permission: 'pay:manage', group: '支付管理' },
] as const

/** 合并页面按任一入口权限开放，具体操作仍各自校验。 */
export function canAccessPage(access: { title?: string, admin?: boolean, permission?: string, permissions?: readonly string[] }, auth: { isAdmin: boolean, can: (permission?: string) => boolean }) {
  return (!access.admin || auth.isAdmin) && (access.permissions ? access.permissions.some(permission => auth.can(permission)) : auth.can(access.permission))
}
