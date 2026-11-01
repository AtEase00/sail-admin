package sail.framework.oss;

/** 存储调用失败，不在对外错误中包含密钥与端点请求信息。 */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        /* 保留服务端根因。 */
        super(message, cause);
    }
}
