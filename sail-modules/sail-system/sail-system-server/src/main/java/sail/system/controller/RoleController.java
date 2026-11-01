package sail.system.controller;

import cn.dev33.satoken.annotation.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;
import sail.system.dto.SystemRequests;
import sail.system.service.*;

/** 角色管理接口。 */
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {
    private final CatalogService catalog;
    private final RbacService rbac;

    @GetMapping
    @SaCheckPermission("system:role:query")
    public ApiResult<?> list(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 目录查询执行功能权限。 */
        return ApiResult.ok(catalog.roles(query));
    }

    @PostMapping
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("创建角色")
    public ApiResult<?> create(@Valid @RequestBody SystemRequests.Role v) {
        /* 写入前校验目录规则。 */
        return ApiResult.ok(catalog.role(null, v));
    }

    @PutMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("修改角色")
    public ApiResult<?> update(@PathVariable long id, @Valid @RequestBody SystemRequests.Role v) {
        /* 更新时保留服务器审计字段。 */
        return ApiResult.ok(catalog.role(id, v));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("删除角色")
    public ApiResult<?> delete(@PathVariable long id) {
        /* 关联校验后删除。 */
        catalog.deleteRole(id);
        return ApiResult.ok(null);
    }

    @GetMapping("/{id}/menus")
    @SaCheckPermission("system:role:query")
    public ApiResult<?> menuIds(@PathVariable long id) {
        /* 查询已分配菜单。 */
        return ApiResult.ok(rbac.menuIds(id));
    }

    @PutMapping("/{id}/menus")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog(value = "分配角色菜单", type = sail.common.core.OperationType.GRANT)
    public ApiResult<?> menus(
            @PathVariable long id, @Valid @RequestBody SystemRequests.Assignment v) {
        /* 原子替换角色权限。 */
        rbac.assignRoleMenus(id, v.ids());
        return ApiResult.ok(null);
    }

    @GetMapping("/{id}/depts")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    public ApiResult<?> deptIds(@PathVariable long id) {
        /* 查询指定部门数据范围。 */
        return ApiResult.ok(rbac.deptIds(id));
    }

    @PutMapping("/{id}/depts")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog(value = "分配角色数据范围", type = sail.common.core.OperationType.GRANT)
    public ApiResult<?> depts(
            @PathVariable long id, @Valid @RequestBody SystemRequests.Assignment v) {
        /* 批量保存指定部门范围。 */
        rbac.assignRoleDepts(id, v.ids());
        return ApiResult.ok(null);
    }
}
