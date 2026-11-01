# Sail Admin Product

## Register

product

## Users

企业后台管理员和具有明确权限的业务操作人员，管理账号、组织、文件、站内消息与扫码支付。

## Product Purpose

将已确认的 Sail Java 基础能力提供为真实可操作的管理界面；所有列表、表单和状态来源于后端接口。

## Brand Personality

清晰、克制、一致。界面沿用用户指定的 shadcn-vue-admin 作为明确参考，复用其导航、主题、字体和 shadcn 组件。

## Anti-references

不展示上游演示收入、虚构用户或模拟登录；不添加与当前后台能力无关的营销页面、社交登录或无效操作入口。

## Design Principles

- 以实际任务组织导航，权限不可用时隐藏操作并由后端再次校验。
- 表单使用明确字段名称和真实资料，不用任意 JSON 编辑器代替业务界面。
- 加载、空数据、失败和成功分别呈现；无法获取的信息明确说明。
- 删除与关单等操作确认具体资源，秘密字段留空表示保留，不回显密文。

## Accessibility & Inclusion

沿用上游响应式与无障碍组件，表单具备标签，消息使用 live region，支持键盘操作与减少动效；未进行运行时无障碍验收。
