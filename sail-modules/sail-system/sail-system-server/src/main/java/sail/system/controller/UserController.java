package sail.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;
import sail.system.dto.SystemRequests;
import sail.system.service.*;

/** 用户资料、角色及岗位分配接口。 */
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService users;
    private final RbacService rbac;

    @GetMapping
    @SaCheckPermission("system:user:query")
    public ApiResult<?> page(
            @jakarta.validation.Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 查询范围由服务端决定。 */
        return ApiResult.ok(users.page(query));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("system:user:query")
    public ApiResult<?> get(@PathVariable long id) {
        /* 详情同样校验数据权限。 */
        return ApiResult.ok(users.get(id));
    }

    @PostMapping
    @SaCheckPermission("system:user:write")
    @OperationLog("创建用户")
    public ApiResult<?> create(@Valid @RequestBody SystemRequests.UserCreate v) {
        /* 禁止将实体直接作为输入。 */
        return ApiResult.ok(users.create(v));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:user:write")
    @OperationLog("更新用户")
    public ApiResult<?> update(
            @PathVariable long id, @Valid @RequestBody SystemRequests.UserUpdate v) {
        /* 敏感字段由独立接口处理。 */
        users.update(id, v);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:user:write")
    @OperationLog("逻辑删除用户并解除授权")
    public ApiResult<?> delete(@PathVariable long id) {
        /* 保留被其他业务引用的账号编号。 */
        users.delete(id);
        return ApiResult.ok(null);
    }

    @PutMapping("/{id}/password")
    @SaCheckPermission("system:user:write")
    @OperationLog(value = "重置用户密码", type = sail.common.core.OperationType.PASSWORD)
    public ApiResult<?> reset(
            @PathVariable long id, @Valid @RequestBody SystemRequests.ResetPassword v) {
        /* 密码不会写入审计描述。 */
        users.reset(id, v.password());
        return ApiResult.ok(null);
    }

    @GetMapping("/{id}/roles")
    @SaCheckPermission("system:user:query")
    public ApiResult<?> roles(@PathVariable long id) {
        /* 关系查询禁止越权。 */
        return ApiResult.ok(rbac.roleIds(id));
    }

    @PutMapping("/{id}/roles")
    @SaCheckPermission("system:role:write")
    @OperationLog(value = "分配用户角色", type = sail.common.core.OperationType.GRANT)
    public ApiResult<?> roles(
            @PathVariable long id, @Valid @RequestBody SystemRequests.Assignment v) {
        /* 角色关系批量替换。 */
        rbac.assignUserRoles(id, v.ids());
        return ApiResult.ok(null);
    }

    @GetMapping("/{id}/posts")
    @SaCheckPermission("system:user:query")
    public ApiResult<?> posts(@PathVariable long id) {
        /* 关系查询沿用用户数据范围。 */
        return ApiResult.ok(rbac.postIds(id));
    }

    @PutMapping("/{id}/posts")
    @SaCheckPermission("system:user:write")
    @OperationLog(value = "分配用户岗位", type = sail.common.core.OperationType.GRANT)
    public ApiResult<?> posts(
            @PathVariable long id, @Valid @RequestBody SystemRequests.Assignment v) {
        /* 岗位校验后批量替换。 */
        users.assignPosts(id, v.ids());
        return ApiResult.ok(null);
    }

    @GetMapping("/{id}/sessions")
    @cn.dev33.satoken.annotation.SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    public ApiResult<?> sessions(@PathVariable long id) {
        // 只返回设备类型和登录时间，不向管理端泄漏用户令牌。
        rbac.checkUserAccess(id);
        return ApiResult.ok(
                cn.dev33.satoken.stp.StpUtil.getTerminalListByLoginId(id).stream()
                        .map(
                                t ->
                                        java.util.Map.of(
                                                "index",
                                                t.getIndex(),
                                                "deviceType",
                                                t.getDeviceType(),
                                                "createdAt",
                                                java.time.LocalDateTime.ofInstant(
                                                        java.time.Instant.ofEpochMilli(
                                                                t.getCreateTime()),
                                                        java.time.ZoneId.of("Asia/Shanghai"))))
                        .toList());
    }
}
