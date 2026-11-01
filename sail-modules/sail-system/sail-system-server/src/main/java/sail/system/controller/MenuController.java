package sail.system.controller;

import cn.dev33.satoken.annotation.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;
import sail.system.dto.SystemRequests;
import sail.system.service.*;

/** 菜单管理接口。 */
@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuController {
    private final CatalogService catalog;

    @GetMapping
    @SaCheckPermission("system:menu:query")
    public ApiResult<?> list(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 目录查询执行功能权限。 */
        return ApiResult.ok(catalog.menus(query));
    }

    @PostMapping
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("创建菜单")
    public ApiResult<?> create(@Valid @RequestBody SystemRequests.Menu v) {
        /* 写入前校验目录规则。 */
        return ApiResult.ok(catalog.menu(null, v));
    }

    @PutMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("修改菜单")
    public ApiResult<?> update(@PathVariable long id, @Valid @RequestBody SystemRequests.Menu v) {
        /* 更新时保留服务器审计字段。 */
        return ApiResult.ok(catalog.menu(id, v));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("删除菜单")
    public ApiResult<?> delete(@PathVariable long id) {
        /* 关联校验后删除。 */
        catalog.deleteMenu(id);
        return ApiResult.ok(null);
    }
}
