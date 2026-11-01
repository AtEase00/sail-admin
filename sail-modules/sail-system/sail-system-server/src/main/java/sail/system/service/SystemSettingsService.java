package sail.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.BusinessException;
import sail.system.entity.SysConfig;
import sail.system.mapper.SysConfigMapper;

import java.util.*;
import java.util.stream.Collectors;

/** 面向管理人员的分组配置，一次保存整组并即时供业务服务读取。 */
@Service
@RequiredArgsConstructor
public class SystemSettingsService implements sail.file.api.FileReferenceGuard {
    private final SysConfigMapper configs;
    private final sail.file.api.FileLookup files;

    /** 返回真实已存值和编辑元数据，不用其他参数代替缺失值。 */
    public List<Map<String, Object>> fields() {
        var saved =
                configs.selectList(Wrappers.<SysConfig>lambdaQuery()).stream()
                        .collect(
                                Collectors.toMap(
                                        SysConfig::getConfigKey, SysConfig::getConfigValue));
        return Arrays.stream(SystemSetting.values())
                .map(
                        field -> {
                            Map<String, Object> view = new LinkedHashMap<>();
                            view.put("key", field.getKey());
                            view.put("label", field.getLabel());
                            view.put("group", field.getGroup());
                            view.put("inputType", field.getInputType());
                            view.put("help", field.getHelp());
                            view.put("value", saved.get(field.getKey()));
                            view.put("min", field.getMin());
                            view.put("max", field.getMax());
                            return view;
                        })
                .toList();
    }

    /** 批量校验通过后才写入，任何一项失败都不会保存半套配置。 */
    @Transactional
    public void save(Map<String, String> input) {
        if (cn.hutool.core.collection.CollUtil.isEmpty(input))
            throw new BusinessException("请选择要保存的配置");
        var saved =
                configs
                        .selectList(
                                Wrappers.<SysConfig>lambdaQuery()
                                        .in(SysConfig::getConfigKey, input.keySet()))
                        .stream()
                        .collect(Collectors.toMap(SysConfig::getConfigKey, item -> item));
        input.forEach(
                (key, value) -> {
                    if (value == null) throw new BusinessException("配置值不能为 null");
                    SystemSetting.find(key).validate(value);
                    // 新上传标志必须是当前操作者拥有的公开图片，不能引用私有附件编号。
                    if ("website.logo".equals(key)
                            && value.matches("^/api/file/public/[0-9]+$")
                            && (saved.get(key) == null
                                    || !Objects.equals(saved.get(key).getConfigValue(), value))) {
                        // 已生效标志允许其他管理员原样保存，只有更换引用才检查上传归属。
                        var file =
                                files.findOwned(
                                        Long.parseLong(value.substring(value.lastIndexOf('/') + 1)),
                                        cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());
                        if (!file.publicRead() || !file.contentType().startsWith("image/"))
                            throw new BusinessException("站点标志必须使用公开图片");
                    }
                });
        input.forEach(
                (key, value) -> {
                    var entity = saved.get(key);
                    if (entity == null) {
                        entity = new SysConfig();
                        entity.setConfigKey(key);
                    }
                    entity.setConfigValue(value);
                    entity.setDescription(SystemSetting.find(key).getHelp());
                    if (entity.getId() == null) configs.insert(entity);
                    else configs.updateById(entity);
                });
    }

    /** 站点公开展示字段单独读取，不对外返回登录策略或其他配置。 */
    public Map<String, String> website() {
        return configs
                .selectList(
                        Wrappers.<SysConfig>lambdaQuery()
                                .in(SysConfig::getConfigKey, "website.title", "website.logo"))
                .stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
    }

    /** 站点正在使用的标志不能从文件管理直接删除，先在配置中更换或恢复默认。 */
    @Override
    public void checkDelete(long fileId) {
        if (configs.exists(
                Wrappers.<SysConfig>lambdaQuery()
                        .eq(SysConfig::getConfigKey, "website.logo")
                        .eq(SysConfig::getConfigValue, "/api/file/public/" + fileId)))
            throw new BusinessException("该图片正在用作站点标志，请先更换标志");
    }
}
