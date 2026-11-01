package sail.system.controller;

import cn.dev33.satoken.annotation.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;
import sail.system.dto.SystemRequests;
import sail.system.service.*;

/** 系统参数管理接口。 */
@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class ConfigController {
    private final CatalogService catalog;
    private final SystemSettingsService settings;

    /** 管理表单获取中文分组、输入约束及实际配置值。 */
    @GetMapping("/settings")
    @SaCheckPermission("system:config:query")
    public ApiResult<?> settings() {
        return ApiResult.ok(settings.fields());
    }

    /** 保存受支持配置，权限与原参数写入保持一致。 */
    @PutMapping("/settings")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("保存系统配置")
    public ApiResult<?> saveSettings(@RequestBody java.util.Map<String, String> input) {
        settings.save(input);
        return ApiResult.ok(null);
    }

    /** 已登录用户读取公开站点展示资料，不包含安全参数。 */
    @GetMapping("/website")
    public ApiResult<?> website() {
        return ApiResult.ok(settings.website());
    }

    @GetMapping
    @SaCheckPermission("system:config:query")
    public ApiResult<?> list(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 目录查询执行功能权限。 */
        return ApiResult.ok(catalog.configs(query));
    }

    @PostMapping
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("创建系统参数")
    public ApiResult<?> create(@Valid @RequestBody SystemRequests.Config v) {
        /* 写入前校验目录规则。 */
        return ApiResult.ok(catalog.config(null, v));
    }

    @PutMapping("/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("修改系统参数")
    public ApiResult<?> update(@PathVariable long id, @Valid @RequestBody SystemRequests.Config v) {
        /* 更新时保留服务器审计字段。 */
        return ApiResult.ok(catalog.config(id, v));
    }
}
