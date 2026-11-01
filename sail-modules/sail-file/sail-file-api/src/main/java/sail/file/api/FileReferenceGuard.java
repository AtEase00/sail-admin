package sail.file.api;

/** 业务附件引用保护，避免删除仍供其他用户访问的文件。 */
public interface FileReferenceGuard {
    /** 存在有效业务引用时拒绝实际对象删除。 */
    void checkDelete(long fileId);
}
