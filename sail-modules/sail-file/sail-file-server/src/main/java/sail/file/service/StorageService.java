package sail.file.service;

import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.BusinessException;
import sail.common.core.ListQuery;
import sail.file.dto.StorageRequest;
import sail.file.entity.*;
import sail.file.mapper.*;
import sail.file.vo.StorageView;
import sail.framework.mybatis.DirectoryQueries;
import sail.framework.oss.*;
import sail.framework.security.SecretCipher;

import java.net.URI;
import java.util.*;

/** 多实例存储配置及默认选择，切换不会改变已有文件的配置归属。 */
@Service
@RequiredArgsConstructor
public class StorageService {
    private final StorageConfigMapper configs;
    private final StorageDefaultMapper defaults;
    private final StoredFileMapper files;
    private final SecretCipher secrets;
    private final LocalObjectStore local;
    private final CloudObjectStore cloud;

    public Map<String, List<StorageView>> grouped(ListQuery query) {
        var wrapper = DirectoryQueries.<StorageConfig>from(query);
        wrapper.eq(StrUtil.isNotBlank(query.getType()), "type", query.getType());
        var status = query.booleanStatus();
        wrapper.eq(status != null, "enabled", status)
                .and(
                        StrUtil.isNotBlank(query.getKeyword()),
                        q ->
                                q.like("name", query.getKeyword())
                                        .or()
                                        .like("endpoint", query.getKeyword())
                                        .or()
                                        .like("bucket", query.getKeyword()));
        // 按存储类型分组，即使没有配置也返回空分组便于管理端展示。
        long selected = defaults.selectById(1L).getConfigId();
        Map<String, List<StorageView>> result = new LinkedHashMap<>();
        for (StorageType type : StorageType.values()) result.put(type.name(), new ArrayList<>());
        configs.selectList(wrapper)
                .forEach(c -> result.get(c.getType()).add(StorageView.from(c, selected)));
        return result;
    }

    @Transactional
    public long save(Long id, StorageRequest v) {
        StorageConfig c = id == null ? new StorageConfig() : get(id);
        boolean referenced =
                id != null
                        && files.selectCount(
                                        Wrappers.<StoredFile>lambdaQuery()
                                                .eq(StoredFile::getStorageId, id))
                                > 0;
        // 已有文件绑定的物理地址不允许变更，否则旧文件会不可访问。
        if (referenced
                && (!c.getType().equals(v.type().name())
                        || !Objects.equals(c.getEndpoint(), v.endpoint())
                        || !Objects.equals(c.getBucket(), v.bucket())
                        || !Objects.equals(c.getRegion(), v.region())))
            throw new BusinessException("已有文件引用此配置，请创建新配置后切换默认存储");
        if (v.type() != StorageType.LOCAL) {
            if (StrUtil.isBlank(v.endpoint()) || StrUtil.isBlank(v.bucket()))
                throw new BusinessException("对象存储必须提供 endpoint 和 bucket");
            URI uri = URI.create(v.endpoint());
            if (!Set.of("http", "https").contains(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || StrUtil.isNotBlank(uri.getQuery()))
                throw new BusinessException("存储 endpoint 不合法");
            if (v.type() == StorageType.MINIO && StrUtil.isBlank(v.region()))
                throw new BusinessException("MinIO 必须明确提供 region");
        }
        if (id != null && !v.enabled() && defaults.selectById(1L).getConfigId().equals(id))
            throw new BusinessException("请先切换默认存储，再停用此配置");
        c.setName(v.name());
        c.setType(v.type().name());
        c.setEndpoint(v.endpoint());
        c.setBucket(v.bucket());
        c.setRegion(v.region());
        c.setEnabled(v.enabled());
        // 更新时空秘密保留原值；非空秘密必须完整提供且加密保存。
        if (StrUtil.isNotBlank(v.accessKey())) c.setAccessKeyCipher(secrets.encrypt(v.accessKey()));
        if (StrUtil.isNotBlank(v.secretKey())) c.setSecretKeyCipher(secrets.encrypt(v.secretKey()));
        if (v.type() != StorageType.LOCAL
                && (c.getAccessKeyCipher() == null || c.getSecretKeyCipher() == null))
            throw new BusinessException("对象存储凭据未配置");
        if (id == null) configs.insert(c);
        else configs.updateById(c);
        return c.getId();
    }

    @Transactional
    public void select(long id) {
        StorageConfig c = get(id);
        if (!Boolean.TRUE.equals(c.getEnabled())) throw new BusinessException("不能选择已停用的存储");
        // 单行更新保证并发切换时始终只有一个默认配置。
        StorageDefault selected = defaults.selectById(1L);
        selected.setConfigId(id);
        defaults.updateById(selected);
    }

    public void delete(long id) {
        if (defaults.selectById(1L).getConfigId().equals(id))
            throw new BusinessException("不能删除当前默认存储");
        if (files.selectCount(Wrappers.<StoredFile>lambdaQuery().eq(StoredFile::getStorageId, id))
                > 0) throw new BusinessException("仍有文件引用此存储");
        // 只删除配置，不删除远端 bucket 或目录。
        configs.deleteById(id);
    }

    public StorageSpec selected() {
        // 新文件读取当前默认值，旧文件使用自身 storageId。
        StorageConfig c = get(defaults.selectById(1L).getConfigId());
        if (!Boolean.TRUE.equals(c.getEnabled())) throw new BusinessException("当前默认存储未启用");
        return spec(c.getId());
    }

    public StorageSpec spec(long id) {
        StorageConfig c = get(id);
        StorageType type = StorageType.valueOf(c.getType());
        // 停用只阻止新上传，保留已有文件的下载和清理能力。
        return new StorageSpec(
                id,
                type,
                c.getEndpoint(),
                c.getBucket(),
                type == StorageType.LOCAL ? null : secrets.decrypt(c.getAccessKeyCipher()),
                type == StorageType.LOCAL ? null : secrets.decrypt(c.getSecretKeyCipher()),
                c.getRegion());
    }

    public ObjectStore client(StorageSpec s) {
        /* 按明确类型路由，不对失败渠道自动降级。 */
        return s.type() == StorageType.LOCAL ? local : cloud;
    }

    private StorageConfig get(long id) {
        /* 未知配置明确报错，不拿默认配置替代。 */
        StorageConfig c = configs.selectById(id);
        if (c == null) throw new BusinessException("存储配置不存在");
        return c;
    }
}
