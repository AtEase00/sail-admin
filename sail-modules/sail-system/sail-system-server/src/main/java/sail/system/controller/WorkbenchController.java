package sail.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.security.SecurityConstants;
import sail.system.dto.WorkbenchViews;
import sail.system.service.WorkbenchService;

/** 工作台只读接口，沿用审计权限与管理员会话查询边界。 */
@RestController
@RequestMapping("/api/system/workbench")
@RequiredArgsConstructor
public class WorkbenchController {
    private final WorkbenchService service;

    /** 返回数据库聚合后的最近七日审计，不提供越权读取入口。 */
    @GetMapping("/audit")
    @SaCheckPermission("system:audit:query")
    public ApiResult<WorkbenchViews.AuditOverview> audit() {
        // 统计与日志页面使用相同权限。
        return ApiResult.ok(service.auditOverview());
    }

    /** 只有超级管理员可以查看其他账号的有效会话。 */
    @GetMapping("/online")
    @SaCheckRole(SecurityConstants.SUPER_ADMIN)
    public ApiResult<WorkbenchViews.OnlineOverview> online() {
        // 查询结果只包含展示字段，不包含会话键或令牌。
        return ApiResult.ok(service.onlineOverview());
    }
}
