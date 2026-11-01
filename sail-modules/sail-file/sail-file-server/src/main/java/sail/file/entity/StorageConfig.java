package sail.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** StorageConfig 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_storage_config")
public class StorageConfig extends BaseEntity {
    private String name;
    private String type;
    private String endpoint;
    private String bucket;
    private String accessKeyCipher;
    private String secretKeyCipher;
    private String region;
    private Boolean enabled;
}
