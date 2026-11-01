import type { Row } from '@/services/types/sail'

/** 展示节点保留真实记录，分组节点只用于旧版扁平权限的模块归类，不提交虚拟编号。 */
export interface TreeItem {
  key: string
  label: string
  row?: Row
  children: TreeItem[]
}
const permissionGroups: Record<string, string> = {
  system: '系统管理',
  file: '文件管理',
  message: '消息管理',
  pay: '支付管理',
  user: '用户管理',
  role: '角色管理',
  menu: '菜单管理',
  dept: '部门管理',
  post: '岗位管理',
  dictionary: '数据字典',
  config: '系统参数',
  audit: '操作审计',
  template: '消息模板',
}
/** 旧初始化数据以权限串作为名称时，用明确的操作含义改善可读性。 */
function nodeLabel(row: Row): string {
  if (row.name !== row.permission || typeof row.permission !== 'string')
    return String(row.name ?? row.label ?? row.id)
  const parts = row.permission.split(':')
  const operation = parts.pop()!
  const actions: Record<string, string> = { query: '查询', write: '维护', upload: '上传文件', delete: '删除文件', manage: '管理', send: '发送消息', create: '创建订单', refund: '申请退款' }
  const target = parts[0] === 'system' || parts[1] === 'template' ? permissionGroups[parts[1]!] : parts[0] === 'file' ? '文件' : parts[0] === 'pay' ? '支付订单' : '消息'
  return ['query', 'write', 'manage'].includes(operation) ? `${actions[operation] ?? operation}${target ?? ''}` : actions[operation] ?? operation
}
/** 优先还原实际父子关系；旧版无父节点的按钮按真实权限前缀归类。 */
export function buildTree(rows: Row[], groupPermissions = false): TreeItem[] {
  const nodes = new Map(rows.map(row => [row.id, { key: String(row.id), label: nodeLabel(row), row, children: [] } as TreeItem]))
  const roots: TreeItem[] = []
  const groups = new Map<string, TreeItem>()
  for (const row of rows) {
    const node = nodes.get(row.id)!
    const visited = new Set<number>([row.id])
    let parentId = row.parentId as number | null
    let cycle = false
    while (parentId && nodes.has(parentId)) {
      if (visited.has(parentId)) {
        cycle = true
        break
      }
      visited.add(parentId)
      parentId = nodes.get(parentId)!.row!.parentId as number | null
    }
    if (!cycle && row.parentId && nodes.has(Number(row.parentId))) {
      nodes.get(Number(row.parentId))!.children.push(node)
      continue
    }
    if (groupPermissions && row.type === 'BUTTON' && !row.parentId && typeof row.permission === 'string') {
      const parts = row.permission.split(':')
      let branch = roots
      let prefix = ''
      for (const part of parts.slice(0, parts[0] === 'system' ? 2 : 1)) {
        prefix += `:${part}`
        if (!groups.has(prefix)) {
          const group: TreeItem = { key: `group${prefix}`, label: permissionGroups[part] ?? part, children: [] }
          groups.set(prefix, group)
          branch.push(group)
        }
        branch = groups.get(prefix)!.children
      }
      branch.push(node)
    }
    else {
      roots.push(node)
    }
  }
  return roots
}
/** 获取分支内实际记录编号，用于父子联动勾选与半选状态。 */
export function branchIds(node: TreeItem): number[] {
  return [...(node.row ? [node.row.id] : []), ...node.children.flatMap(branchIds)]
}
/** 过滤树时保留匹配节点的祖先和匹配分支，防止搜索结果失去上下文。 */
export function filterTree(nodes: TreeItem[], keyword: string): TreeItem[] {
  const value = keyword.trim().toLowerCase()
  if (!value)
    return nodes
  return nodes.flatMap((node) => {
    if (`${node.label} ${node.row?.permission ?? ''} ${node.row?.path ?? ''}`.toLowerCase().includes(value))
      return [node]
    const children = filterTree(node.children, value)
    return children.length ? [{ ...node, children }] : []
  })
}
