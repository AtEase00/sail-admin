package sail.system.controller;

import cn.dev33.satoken.annotation.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;
import sail.system.dto.SystemRequests;
import sail.system.service.*;

/** 岗位管理接口。 */
@RestController
@RequestMapping("/api/system/posts")
@RequiredArgsConstructor
public class PostController {
    private final CatalogService catalog;

    @GetMapping
    @SaCheckPermission("system:post:query")
    public ApiResult<?> list(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 目录查询执行功能权限。 */
        return ApiResult.ok(catalog.posts(query));
    }

    @PostMapping
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("创建岗位")
    public ApiResult<?> create(@Valid @RequestBody SystemRequests.Post v) {
        /* 写入前校验目录规则。 */
        return ApiResult.ok(catalog.post(null, v));
    }

    @PutMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("修改岗位")
    public ApiResult<?> update(@PathVariable long id, @Valid @RequestBody SystemRequests.Post v) {
        /* 更新时保留服务器审计字段。 */
        return ApiResult.ok(catalog.post(id, v));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("删除岗位")
    public ApiResult<?> delete(@PathVariable long id) {
        /* 关联检查在业务层完成。 */
        catalog.deletePost(id);
        return ApiResult.ok(null);
    }
}
