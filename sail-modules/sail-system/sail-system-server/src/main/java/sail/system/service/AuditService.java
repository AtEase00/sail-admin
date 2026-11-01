package sail.system.service;

import lombok.RequiredArgsConstructor;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import sail.common.core.AuditEvent;
import sail.system.entity.SysAudit;
import sail.system.mapper.SysAuditMapper;

/** 操作与登录审计持久化，业务回滚也保留操作结果。 */
@Service
@RequiredArgsConstructor
public class AuditService {
    private final SysAuditMapper mapper;

    public sail.common.core.PageResult<SysAudit> page(sail.common.core.ListQuery query) {
        // 分页及检索条件共用请求模型，权限范围仍由当前会话决定。
        long page = query.getPage(), size = query.getSize();
        String keyword = query.getKeyword();
        var range = query.createdRange();
        // 审计仅可检索，统一返回受控分页结构。
        var p =
                mapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                                page, size),
                        com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysAudit>lambdaQuery()
                                .ge(range.from() != null, SysAudit::getCreatedAt, range.from())
                                .le(range.to() != null, SysAudit::getCreatedAt, range.to())
                                .and(
                                        cn.hutool.core.util.StrUtil.isNotBlank(keyword),
                                        condition ->
                                                condition
                                                        .like(SysAudit::getAction, keyword)
                                                        .or()
                                                        .like(SysAudit::getTraceId, keyword))
                                .eq(
                                        cn.hutool.core.util.StrUtil.isNotBlank(
                                                query.getRequestMethod()),
                                        SysAudit::getRequestMethod,
                                        query.getRequestMethod())
                                .eq(
                                        cn.hutool.core.util.StrUtil.isNotBlank(
                                                query.getOperationType()),
                                        SysAudit::getOperationType,
                                        query.getOperationType())
                                .eq(
                                        query.booleanStatus() != null,
                                        SysAudit::getSuccess,
                                        query.booleanStatus())
                                .orderByDesc(SysAudit::getId));
        return new sail.common.core.PageResult<>(p.getTotal(), page, size, p.getRecords());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        // 仅保存最小必要操作信息，避免敏感参数进入审计库。
        SysAudit log = new SysAudit();
        log.setUserId(event.userId());
        log.setAction(event.action());
        log.setSuccess(event.success());
        log.setTraceId(event.traceId());
        // 请求结束快照只包含脱敏字段，真实 HTTP 结果与业务操作一起保留。
        log.setOperationType(event.operationType());
        log.setRequestMethod(event.requestMethod());
        log.setRequestUrl(event.requestUrl());
        log.setRequestParams(event.requestParams());
        log.setRequestBody(event.requestBody());
        log.setResponseBody(event.responseBody());
        log.setHttpStatus(event.httpStatus());
        log.setResponseCode(event.responseCode());
        log.setDurationMs(event.durationMs());
        log.setErrorMessage(event.errorMessage());
        mapper.insert(log);
    }
}
