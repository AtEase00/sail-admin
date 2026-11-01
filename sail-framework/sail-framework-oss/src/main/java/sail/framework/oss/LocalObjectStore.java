package sail.framework.oss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;

/** 本地文件适配，每个配置使用独立目录且禁止跟随符号链接逃逸。 */
@Component
public class LocalObjectStore implements ObjectStore {
    private final Path root;

    public LocalObjectStore(@Value("${sail.storage.local-root:./storage}") String root) {
        /* 将允许访问的根目录固定在服务端配置。 */
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    private Path resolve(StorageSpec spec, String key) throws IOException {
        // key 不得为绝对路径，归一化后必须位于该配置目录下。
        Files.createDirectories(root);
        Path allowed = root.toRealPath();
        Path base = allowed.resolve(Long.toString(spec.id()));
        // 配置编号目录也必须检查，不能通过预先放置的目录链接越过存储根目录。
        if (Files.isSymbolicLink(base)) throw new IllegalArgumentException("配置目录包含符号链接");
        Files.createDirectories(base);
        base = base.toRealPath();
        if (!base.startsWith(allowed)) throw new IllegalArgumentException("配置目录越界");
        Path relative = Path.of(key);
        if (relative.isAbsolute()) throw new IllegalArgumentException("禁止绝对文件路径");
        Path path = base.resolve(relative).normalize();
        if (!path.startsWith(base) || path.equals(base))
            throw new IllegalArgumentException("文件路径越界");
        Path current = base;
        for (Path part : base.relativize(path)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("文件路径包含符号链接");
        }
        return path;
    }

    @Override
    public void upload(StorageSpec spec, String key, InputStream input, long length, String type) {
        try {
            Path target = resolve(spec, key);
            Files.createDirectories(target.getParent());
            // 写入临时文件后原子移动，下载端不读取部分写入内容。
            Path temp = Files.createTempFile(target.getParent(), "upload-", ".part");
            try {
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
                if (Files.size(temp) != length) throw new IOException("上传字节数不匹配");
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            throw new StorageException("本地文件写入失败", ex);
        }
    }

    @Override
    public InputStream download(StorageSpec spec, String key) {
        // 返回流交由业务层在响应完成时关闭。
        try {
            return Files.newInputStream(resolve(spec, key));
        } catch (IOException ex) {
            throw new StorageException("本地文件读取失败", ex);
        }
    }

    @Override
    public void delete(StorageSpec spec, String key) {
        // 删除不存在的对象视为幂等成功。
        try {
            Files.deleteIfExists(resolve(spec, key));
        } catch (IOException ex) {
            throw new StorageException("本地文件删除失败", ex);
        }
    }
}
