package sail.system.controller;

import cn.dev33.satoken.annotation.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;
import sail.system.dto.SystemRequests;
import sail.system.service.*;

/** 部门管理接口。 */
@RestController
@RequestMapping("/api/system/depts")
@RequiredArgsConstructor
public class DeptController {
    private final CatalogService catalog;

    @GetMapping
    @SaCheckPermission("system:dept:query")
    public ApiResult<?> list(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 目录查询执行功能权限。 */
        return ApiResult.ok(catalog.depts(query));
    }

    @PostMapping
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("创建部门")
    public ApiResult<?> create(@Valid @RequestBody SystemRequests.Dept v) {
        /* 写入前校验目录规则。 */
        return ApiResult.ok(catalog.dept(null, v));
    }

    @PutMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("修改部门")
    public ApiResult<?> update(@PathVariable long id, @Valid @RequestBody SystemRequests.Dept v) {
        /* 更新时保留服务器审计字段。 */
        return ApiResult.ok(catalog.dept(id, v));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("删除部门")
    public ApiResult<?> delete(@PathVariable long id) {
        /* 关联校验后删除。 */
        catalog.deleteDept(id);
        return ApiResult.ok(null);
    }
}
