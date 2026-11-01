package sail.file.api;

/** 跨模块文件归属查询，不暴露物理路径与存储秘密。 */
public interface FileLookup {
    /** 按明确所有者查找附件，所有者不匹配时拒绝返回。 */
    Attachment findOwned(long fileId, long ownerId);

    /** 业务模块完成收件关系鉴权后，仍按原文件所有者定位真实对象。 */
    java.io.InputStream openOwned(long fileId, long ownerId);

    /** 消息已完成接收关系校验后读取旧版 Office 或幻灯片的真实文本。 */
    String previewOwned(long fileId, long ownerId) throws java.io.IOException;

    /** 业务附件只包含引用编号和展示信息。 */
    record Attachment(long id, String name, String contentType, long size, boolean publicRead) {}
}
