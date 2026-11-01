package sail.file.controller;

import cn.dev33.satoken.annotation.SaCheckRole;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.file.dto.StorageRequest;
import sail.file.service.StorageService;
import sail.framework.log.OperationLog;

/** 存储实例按类型分组、启停与默认选择接口。 */
@RestController
@RequestMapping("/api/file/storages")
@SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
@RequiredArgsConstructor
public class StorageController {
    private final StorageService storage;

    @GetMapping
    public ApiResult<?> list(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 敏感配置只返回已配置标志。 */
        return ApiResult.ok(storage.grouped(query));
    }

    @PostMapping
    @OperationLog("创建文件存储配置")
    public ApiResult<?> create(@Valid @RequestBody StorageRequest v) {
        /* 可为同一类型创建多个实例。 */
        return ApiResult.ok(storage.save(null, v));
    }

    @PutMapping("/{id}")
    @OperationLog("修改文件存储配置")
    public ApiResult<?> update(@PathVariable long id, @Valid @RequestBody StorageRequest v) {
        /* 有文件引用时禁止改变物理位置。 */
        return ApiResult.ok(storage.save(id, v));
    }

    @PutMapping("/{id}/default")
    @OperationLog("切换默认文件存储")
    public ApiResult<?> select(@PathVariable long id) {
        /* 数据库单行决定新上传归属。 */
        storage.select(id);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/{id}")
    @OperationLog("删除文件存储配置")
    public ApiResult<?> delete(@PathVariable long id) {
        /* 被文件引用的配置必须保留。 */
        storage.delete(id);
        return ApiResult.ok(null);
    }
}
