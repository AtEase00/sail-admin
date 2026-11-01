package sail.system.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.system.dto.WorkbenchViews;
import sail.system.mapper.SysUserMapper;
import sail.system.mapper.WorkbenchMapper;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 工作台真实审计统计与有效登录会话检索。 */
@Service
@RequiredArgsConstructor
public class WorkbenchService {
    private final WorkbenchMapper audits;
    private final SysUserMapper users;

    /** 同一只读事务内按七个自然日汇总，缺失日期表示该日确无记录。 */
    @Transactional(readOnly = true)
    public WorkbenchViews.AuditOverview auditOverview() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.toLocalDate().minusDays(6).atStartOfDay();
        var byDate =
                audits.days(from, to).stream()
                        .collect(
                                Collectors.toMap(
                                        WorkbenchViews.AuditDay::date, Function.identity()));
        // 补齐无操作的日期，确保横轴连续；接口失败直接上抛，不替换为零。
        var days =
                from.toLocalDate()
                        .datesUntil(to.toLocalDate().plusDays(1))
                        .map(
                                date ->
                                        byDate.getOrDefault(
                                                date, new WorkbenchViews.AuditDay(date, 0, 0, 0)))
                        .toList();
        return new WorkbenchViews.AuditOverview(
                from, to, days, audits.actions(from, to), audits.recent(from, to));
    }

    /** 在线定义为仍有效且未超过活跃超时的登录会话，查询不会续期会话。 */
    public WorkbenchViews.OnlineOverview onlineOverview() {
        Map<Long, List<Long>> loginTimes = new HashMap<>();
        // Sa-Token Redis DAO 使用 SCAN 搜索账号会话，不使用阻塞式 KEYS。
        for (String key : StpUtil.searchSessionId("", 0, -1, false)) {
            var session = StpUtil.getSessionBySessionId(key);
            if (session == null) continue;
            for (var terminal : session.getTerminalList()) {
                Object loginId = StpUtil.getLoginIdByToken(terminal.getTokenValue());
                if (loginId == null) continue;
                // 登录框架返回的真实账号编号用于批量关联，不暴露内部令牌。
                loginTimes
                        .computeIfAbsent(
                                Long.valueOf(loginId.toString()), ignored -> new ArrayList<>())
                        .add(terminal.getCreateTime());
            }
        }
        if (loginTimes.isEmpty()) return new WorkbenchViews.OnlineOverview(0, 0, List.of());
        List<WorkbenchViews.OnlineUser> online = new ArrayList<>();
        // 分批读取账号资料，排除被删除或停用的账号，避免逐个账号发起 SQL。
        for (var ids : CollUtil.split(new ArrayList<>(loginTimes.keySet()), 500)) {
            for (var user : users.selectByIds(ids)) {
                if (!Integer.valueOf(1).equals(user.getStatus())) continue;
                var times = loginTimes.get(user.getId());
                var latest =
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(Collections.max(times)),
                                ZoneId.systemDefault());
                online.add(
                        new WorkbenchViews.OnlineUser(
                                user.getId(),
                                user.getUsername(),
                                user.getNickname(),
                                times.size(),
                                latest));
            }
        }
        online.sort(
                Comparator.comparing(WorkbenchViews.OnlineUser::latestLoginAt)
                        .reversed()
                        .thenComparingLong(WorkbenchViews.OnlineUser::id));
        return new WorkbenchViews.OnlineOverview(
                online.size(),
                online.stream().mapToLong(WorkbenchViews.OnlineUser::sessions).sum(),
                online.stream().limit(8).toList());
    }
}
