package sail.job;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;

@RestController
@RequestMapping("/api/system/jobs")
@RequiredArgsConstructor
public class JobController {
    private final JobService service;
    private final JobStore store;
    private final HandlerRegistry handlers;

    @GetMapping
    @SaCheckPermission("system:job:query")
    public ApiResult<?> list(@Valid @ModelAttribute JobModels.Query q) {
        return ApiResult.ok(store.jobs(q));
    }

    @GetMapping("/handlers")
    @SaCheckPermission("system:job:query")
    public ApiResult<?> handlers() {
        return ApiResult.ok(handlers.options());
    }

    @PostMapping("/preview")
    @SaCheckPermission("system:job:query")
    public ApiResult<?> preview(@Valid @RequestBody JobModels.Input input) {
        return ApiResult.ok(service.preview(input));
    }

    @PostMapping
    @SaCheckPermission("system:job:create")
    @OperationLog("新增定时任务")
    public ApiResult<?> create(@Valid @RequestBody JobModels.Input input) {
        return ApiResult.ok(service.save(null, input));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:job:update")
    @OperationLog("修改定时任务")
    public ApiResult<?> update(@PathVariable long id, @Valid @RequestBody JobModels.Input input) {
        return ApiResult.ok(service.save(id, input));
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission("system:job:control")
    @OperationLog("变更定时任务状态")
    public ApiResult<?> status(@PathVariable long id, @Valid @RequestBody JobModels.Control input) {
        service.control(id, input);
        return ApiResult.ok(null);
    }

    @PostMapping("/{id}/run")
    @SaCheckPermission("system:job:run")
    @OperationLog("手动执行定时任务")
    public ApiResult<?> run(@PathVariable long id, @Valid @RequestBody JobModels.Version input) {
        return ApiResult.ok(
                service.trigger(
                        id, input.version(), null, StpUtil.getLoginIdAsLong(), actorName()));
    }

    @GetMapping("/executions")
    @SaCheckPermission("system:job:log:query")
    public ApiResult<?> executions(@Valid @ModelAttribute JobModels.Query q) {
        return ApiResult.ok(store.runs(q));
    }

    @GetMapping("/executions/{id}")
    @SaCheckPermission("system:job:log:query")
    public ApiResult<?> execution(@PathVariable long id) {
        return ApiResult.ok(store.run(id));
    }

    @PostMapping("/executions/{id}/retry")
    @SaCheckPermission({"system:job:run", "system:job:log:query"})
    @OperationLog("重试定时任务")
    public ApiResult<?> retry(@PathVariable long id, @Valid @RequestBody JobModels.Version input) {
        var run = store.run(id);
        return ApiResult.ok(
                service.trigger(
                        run.jobId(), input.version(), id, StpUtil.getLoginIdAsLong(), actorName()));
    }

    @PostMapping("/executions/{id}/resolve")
    @SaCheckPermission({"system:job:control", "system:job:log:query"})
    @OperationLog("确认失联任务已停止")
    public ApiResult<?> resolve(
            @PathVariable long id, @Valid @RequestBody JobModels.Resolve input) {
        service.resolve(id, input.reason());
        return ApiResult.ok(null);
    }

    private String actorName() {
        return store.db()
                .queryForObject(
                        "SELECT nickname FROM sys_user WHERE id=? AND deleted=0",
                        String.class,
                        StpUtil.getLoginIdAsLong());
    }
}
