---
name: shadcn-vue-admin
description: Build and maintain the shadcn-vue-admin Vue 3 + Vite + TypeScript admin dashboard with shadcn-vue, Tailwind, Pinia, Vue Router, i18n, and TanStack Query. Use for UI/layout changes, page additions, routing updates, theme/auth work, and component integration in this repo.
license: MIT
metadata:
  repository: Whbbit1999/shadcn-vue-admin
  package-manager: pnpm
  framework: vue
  language: typescript
---

## Purpose and scope

Maintain this Vue 3 admin dashboard repository: pages and layouts, component integration, routing/auth, theming and i18n, data tables, and form validation.

## Codebase map

- App entry: `src/main.ts`, `src/App.vue`
- Routing: `src/router/`
- Layouts and pages: `src/layouts/`, `src/pages/`
- Components: `src/components/` (including shadcn-vue style UI)
- State: `src/stores/`
- Composables: `src/composables/`
- Utils and constants: `src/utils/`, `src/lib/`, `src/constants/`
- Plugins: `src/plugins/`

## References

- System knowledge map: [references/SYSTEM_KNOWLEDGE_MAP.md](references/SYSTEM_KNOWLEDGE_MAP.md)
- Testing strategy: [references/testing-strategy.md](references/testing-strategy.md)

## Standard workflow

1. Read existing implementations in the target directory and reuse established patterns and styles.
2. Prefer existing shadcn-vue components and shared utilities to avoid duplication.
3. Only change public APIs when necessary; avoid large-scale formatting unrelated code.

## Commands and checks

- Dev server: `pnpm dev`
- Build (CI-like check): `pnpm build`
- Lint fix: `pnpm lint:fix`

Requirements:

- Run `pnpm build` for any non-copy-only change.
- Run `pnpm lint:fix` after code changes.
- If you modify core logic (`src/lib/**`, `src/utils/**`, `src/composables/**`, `src/services/**`, `src/router/**`, `src/stores/**`):
  - If test scripts exist (e.g. `pnpm test`/`pnpm test:unit`), add/update tests and run them.
  - If no test scripts exist, tests are optional but recommended; include “Testing Notes” in the change description.

## Design and implementation conventions

- Use Vue 3 Composition API with TypeScript.
- Prefer tanstack/vue-form + zod for form validation.
- Follow existing theming strategy in `src/assets/` and `src/stores/theme.ts`.
- Follow the existing structure for i18n in `src/plugins/i18n/`.

## Common task guides

### Add a page

1. Create a page component under `src/pages/`.
2. Register routing/menu via `src/router/` if needed.
3. Use existing layouts and shared components for consistent spacing and typography.

### Add a component

1. Reuse `src/components/ui/` and existing shadcn-vue components first.
2. If it should be shared, place it under `src/components/` to avoid page-level duplication.

### Update theme/styles

1. Prefer Tailwind and theme files in `src/assets/`.
2. Avoid heavy inline styles; keep components maintainable.

### Output requirements

After changes, provide a concise summary and list any commands run (if any).

## Sail 页面文案与操作反馈（项目强制约定）

- 主内容区禁止重复展示导航、面包屑或页签已有的页面名称，例如“定时任务”“执行记录”“个人中心”。直接从筛选区、工具栏或业务内容开始，不保留空标题容器和占位间距，不通过 `BasicPage` / `BasicHeader` 等组件重新引入。业务分区、弹窗标题和真实数据名称按需保留。
- 同一工作流的操作与记录放在同一入口：执行记录属于定时任务，消息发布属于发布记录。筛选条件统一放入 `QueryBar`，通过 `fields` 扩展，不在筛选卡片外另摆一排字段。合并页面时保留原操作权限、未保存确认及旧链接兼容，不复制页面实现。
- 页面和表单只呈现必要的业务分区标题、字段、真实数据、状态、操作。禁止自行添加功能自述、页面副标题、表单教学说明和技术实现说明，包括“管理……”“支持……”“每 5 秒刷新”“列表时间为上海时区”“同一任务禁止并发”“带 * 的为必填项”“所有层级默认展开”等。不要把删除的说明移到 tooltip、占位符或隐藏文案中。
- 不新增泛化帮助段落。输入示例或单位仅在完成填写确有必要时用简短字段/占位符表达。必要的删除、退款、失联解锁等确认只说明当前动作及实际后果。真实数据详情（包括执行日志的追踪号）不属于功能自述。
- 全项目操作反馈统一调用 `src/lib/message.ts` 的 `message.success/error/warning/info`，唯一渲染组件为 `GlobalMessage.vue`。顶部居中、向下进入、向上退出，四种状态区分图标和颜色；不新增局部 Toast/Message 实例或额外通知库。
- 接口错误和操作错误通过全局 Message 展示，不能在登录框、表单、页头或页尾重复渲染错误正文。需要保留加载失败/重试状态的组件使用 `useMessageError`，不把请求失败伪装成空数据或成功。原生表单校验复用 `SailForm`。
- `ApiError` 保存 status/traceId，用户反馈只显示简洁业务原因；追踪号留在 HTTP 响应、日志和诊断详情。复用后端统一异常处理，不在各 Controller 重复拼错误或追踪信息。
- 修改页面或表单时检查完整页面及共用弹窗，而非只检查新增区域；复用现有组件，禁止复制消息样式、定时消失和错误归一化逻辑。
