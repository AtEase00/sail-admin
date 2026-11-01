package sail.framework.oss;

import java.io.InputStream;

/** 文件对象读写契约，key 由服务端生成。 */
public interface ObjectStore {
    /** 将指定长度的实际内容流写入服务端生成的对象键。 */
    void upload(
            StorageSpec storage, String key, InputStream content, long length, String contentType);

    /** 打开指定配置的对象流，调用方负责关闭流及其关联 SDK 资源。 */
    InputStream download(StorageSpec storage, String key);

    /** 幂等删除单个对象，不允许扩展为目录或 bucket 删除。 */
    void delete(StorageSpec storage, String key);
}
