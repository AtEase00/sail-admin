package sail.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;

import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;

/** 审计检索与账号会话强制下线接口。 */
@RestController
@RequestMapping("/api/system/audit")
@RequiredArgsConstructor
public class AuditController {
    private final sail.system.service.AuditService audit;

    @GetMapping
    @SaCheckPermission("system:audit:query")
    public ApiResult<?> page(
            @jakarta.validation.Valid @ModelAttribute sail.common.core.ListQuery query) {
        // 审计只提供查询，不暴露修改删除接口。
        return ApiResult.ok(audit.page(query));
    }

    @DeleteMapping("/sessions/{userId}")
    @cn.dev33.satoken.annotation.SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @sail.framework.log.OperationLog(
            value = "强制账号下线",
            type = sail.common.core.OperationType.FORCE_LOGOUT)
    public ApiResult<?> kickout(@PathVariable long userId) {
        /* 撤销目标账号的全部有效会话。 */
        StpUtil.kickout(userId);
        return ApiResult.ok(null);
    }
}
