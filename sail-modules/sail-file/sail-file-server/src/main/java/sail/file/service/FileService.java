package sail.file.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import sail.common.core.*;
import sail.file.entity.StoredFile;
import sail.file.mapper.StoredFileMapper;
import sail.framework.oss.*;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

/** 文件元数据与实际对象管理，所有私有文件执行归属校验。 */
@Service
@RequiredArgsConstructor
public class FileService {
    private final StoredFileMapper files;
    private final StorageService storage;
    private final OfficeTextPreview officePreview;
    private final org.springframework.beans.factory.ObjectProvider<sail.file.api.FileReferenceGuard>
            references;

    @Value("${sail.storage.max-bytes:20971520}")
    private long maxBytes;

    public StoredFile upload(MultipartFile part, boolean publicRead) throws IOException {
        if (part.isEmpty() || part.getSize() > maxBytes) throw new BusinessException("文件为空或超过大小限制");
        String name = part.getOriginalFilename();
        if (StrUtil.isBlank(name)
                || name.length() > 200
                || name.contains("/")
                || name.contains("\\")) throw new BusinessException("文件名不合法");
        // 类型白名单与内容检测共同校验，原始文件名不参与存储路径拼接。
        String ext = StrUtil.subAfter(name, '.', true).toLowerCase(Locale.ROOT);
        if (!FilePolicy.EXTENSIONS.contains(ext)) throw new BusinessException("文件扩展名不允许上传");
        String type;
        try (InputStream input = part.getInputStream()) {
            type = new org.apache.tika.Tika().detect(input, name);
        }
        if (!FilePolicy.CONTENT_TYPES.contains(type)) throw new BusinessException("文件内容类型不允许上传");
        StorageSpec spec = storage.selected();
        String key = LocalDate.now() + "/" + UUID.randomUUID() + "." + ext;
        try (InputStream input = part.getInputStream()) {
            storage.client(spec).upload(spec, key, input, part.getSize(), type);
        }
        StoredFile file = new StoredFile();
        file.setOwnerId(StpUtil.getLoginIdAsLong());
        file.setStorageId(spec.id());
        file.setObjectKey(key);
        file.setOriginalName(name);
        file.setContentType(type);
        file.setSize(part.getSize());
        file.setPublicRead(publicRead);
        file.setStatus("READY");
        try {
            files.insert(file);
        } catch (RuntimeException ex) {
            // 元数据落库失败时删除已上传对象；清理失败根因附加到原异常。
            try {
                storage.client(spec).delete(spec, key);
            } catch (RuntimeException cleanup) {
                ex.addSuppressed(cleanup);
            }
            throw ex;
        }
        return file;
    }

    public PageResult<StoredFile> page(sail.common.core.ListQuery query) {
        // 分页及检索条件共用请求模型，权限范围仍由当前会话决定。
        long page = query.getPage(), size = query.getSize();
        String keyword = query.getKeyword();
        var range = query.createdRange();
        // 普通用户仅能枚举自己的文件，管理权限允许全局检索。
        var q =
                Wrappers.<StoredFile>lambdaQuery()
                        .ge(range.from() != null, StoredFile::getCreatedAt, range.from())
                        .le(range.to() != null, StoredFile::getCreatedAt, range.to())
                        .and(
                                cn.hutool.core.util.StrUtil.isNotBlank(keyword),
                                condition -> condition.like(StoredFile::getOriginalName, keyword))
                        .eq(
                                query.booleanStatus() != null,
                                StoredFile::getPublicRead,
                                query.booleanStatus())
                        .eq(StoredFile::getStatus, "READY")
                        .orderByDesc(StoredFile::getId);
        if (!StpUtil.hasPermission("file:manage"))
            q.eq(StoredFile::getOwnerId, StpUtil.getLoginIdAsLong());
        var p = files.selectPage(new Page<>(page, size), q);
        return new PageResult<>(p.getTotal(), page, size, p.getRecords());
    }

    public StoredFile get(long id, boolean publicOnly) {
        StoredFile f = files.selectById(id);
        if (f == null || !"READY".equals(f.getStatus())) throw new BusinessException(404, "文件不存在");
        // 匿名下载只能读取明确公开的对象；私有对象不凭编号放行。
        if (publicOnly) {
            if (!Boolean.TRUE.equals(f.getPublicRead())) throw new BusinessException(404, "文件不存在");
        } else if (f.getOwnerId() != StpUtil.getLoginIdAsLong()
                && !StpUtil.hasPermission("file:manage"))
            throw new BusinessException(403, "没有文件访问权限");
        return f;
    }

    public InputStream open(StoredFile f) {
        /* 按文件原存储配置定位，默认配置切换不影响读取。 */
        var s = storage.spec(f.getStorageId());
        return storage.client(s).download(s, f.getObjectKey());
    }

    /** 先由调用方完成文件归属校验，再读取真实文档文本，解析失败保留明确错误。 */
    public String preview(StoredFile file) throws IOException {
        try (var input = open(file)) {
            return officePreview.read(file.getOriginalName(), input);
        }
    }

    public void delete(long id) {
        StoredFile f = get(id, false);
        // 被消息等业务引用的附件必须先解除引用，禁止破坏已发布内容。
        references.orderedStream().forEach(reference -> reference.checkDelete(id));
        var s = storage.spec(f.getStorageId());
        // 保留待删除记录，远端成功后才标记删除，失败可以再次操作。
        storage.client(s).delete(s, f.getObjectKey());
        // 实际对象删除成功后按本行主键标记删除，业务状态不承担删除语义。
        files.deleteById(id);
    }
}
