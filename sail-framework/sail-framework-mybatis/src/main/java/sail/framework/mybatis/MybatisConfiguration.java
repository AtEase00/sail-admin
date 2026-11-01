package sail.framework.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.*;

import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;

import java.time.LocalDateTime;

/** MyBatis-Plus 分页、危险 SQL 拦截和审计字段填充。 */
@org.springframework.boot.autoconfigure.AutoConfiguration
@MapperScan(basePackages = "sail", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MybatisConfiguration {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 分页限制在数据库层生效，禁止无条件批量更新或删除。
        var plugin = new MybatisPlusInterceptor();
        var page = new PaginationInnerInterceptor(DbType.MYSQL);
        page.setMaxLimit(200L);
        plugin.addInnerInterceptor(page);
        plugin.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return plugin;
    }

    @Bean
    public MetaObjectHandler auditFields() {
        // 由服务端统一填充时间，不接受客户端伪造审计字段。
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject meta) {
                strictInsertFill(meta, "createdAt", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(meta, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject meta) {
                setFieldValByName("updatedAt", LocalDateTime.now(), meta);
            }
        };
    }
}
