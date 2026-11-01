package sail.file.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import sail.common.core.BusinessException;
import sail.file.api.FileLookup;
import sail.file.mapper.StoredFileMapper;

/** 文件查询契约本地实现，业务模块无需接触文件 Mapper。 */
@Service
@RequiredArgsConstructor
public class FileLookupImpl implements FileLookup {
    private final StoredFileMapper files;
    private final FileService service;

    @Override
    public java.io.InputStream openOwned(long id, long owner) {
        // 复用归属校验后只按文件原存储实例读取，不受当前默认存储变化影响。
        findOwned(id, owner);
        return service.open(files.selectById(id));
    }

    @Override
    public String previewOwned(long id, long owner) throws java.io.IOException {
        // 仍按消息发布者的文件归属读取，不借预览接口扩大其他文件权限。
        findOwned(id, owner);
        return service.preview(files.selectById(id));
    }

    @Override
    public Attachment findOwned(long id, long owner) {
        // 调用者明确提供业务所有人，不能用任意文件编号绕过归属校验。
        var file = files.selectById(id);
        if (file == null || file.getOwnerId() != owner || !"READY".equals(file.getStatus()))
            throw new BusinessException(404, "业务附件不存在");
        return new Attachment(
                id,
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                Boolean.TRUE.equals(file.getPublicRead()));
    }
}
