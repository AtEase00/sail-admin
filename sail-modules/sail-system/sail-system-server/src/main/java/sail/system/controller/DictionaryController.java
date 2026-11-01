package sail.system.controller;

import cn.dev33.satoken.annotation.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;
import sail.system.dto.SystemRequests;
import sail.system.service.*;

/** 字典管理接口。 */
@RestController
@RequestMapping("/api/system/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {
    private final CatalogService catalog;
    private final DictionaryTypeService types;

    /** 业务组件使用已启用的真实字典，不依赖字典管理权限。 */
    @GetMapping("/options")
    public ApiResult<?> options() {
        return ApiResult.ok(types.options());
    }

    /** 类型与具体数据分别查询，字典管理页面可按类型浏览。 */
    @GetMapping("/types")
    @SaCheckPermission("system:dictionary:query")
    public ApiResult<?> types(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        return ApiResult.ok(types.list(query));
    }

    /** 新增稳定类型编码及中文名称。 */
    @PostMapping("/types")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("创建字典类型")
    public ApiResult<?> createType(@Valid @RequestBody SystemRequests.DictionaryType input) {
        return ApiResult.ok(types.save(null, input));
    }

    /** 类型编码修改与数据迁移保持原子性。 */
    @PutMapping("/types/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("修改字典类型")
    public ApiResult<?> updateType(
            @PathVariable long id, @Valid @RequestBody SystemRequests.DictionaryType input) {
        return ApiResult.ok(types.save(id, input));
    }

    /** 删除前校验类型保护及数据引用。 */
    @DeleteMapping("/types/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("删除字典类型")
    public ApiResult<?> deleteType(@PathVariable long id) {
        types.delete(id);
        return ApiResult.ok(null);
    }

    @GetMapping
    @SaCheckPermission("system:dictionary:query")
    public ApiResult<?> list(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 目录查询执行功能权限。 */
        return ApiResult.ok(catalog.dictionaries(query));
    }

    @PostMapping
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("创建字典")
    public ApiResult<?> create(@Valid @RequestBody SystemRequests.Dictionary v) {
        /* 写入前校验目录规则。 */
        return ApiResult.ok(catalog.dictionary(null, v));
    }

    @PutMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("修改字典")
    public ApiResult<?> update(
            @PathVariable long id, @Valid @RequestBody SystemRequests.Dictionary v) {
        /* 更新时保留服务器审计字段。 */
        return ApiResult.ok(catalog.dictionary(id, v));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("删除字典")
    public ApiResult<?> delete(@PathVariable long id) {
        /* 关联检查在业务层完成。 */
        catalog.deleteDictionary(id);
        return ApiResult.ok(null);
    }
}
