package sail.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** StoredFile 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_object")
public class StoredFile extends BaseEntity {
    private Long ownerId;
    private Long storageId;
    private String objectKey;
    private String originalName;
    private String contentType;
    private Long size;
    private Boolean publicRead;
    private String status;
}
