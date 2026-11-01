package sail.pay.mapper;

import org.apache.ibatis.annotations.*;

/** 数据库任务租约，避免多实例同时扫描同一批补偿任务。 */
@Mapper
public interface JobLeaseMapper {
    @Update(
            "UPDATE job_lease SET owner=#{owner}, lease_until=#{until} WHERE"
                    + " name='pay-compensation' AND deleted=0 AND lease_until < #{now}")
    int acquire(
            @Param("owner") String owner,
            @Param("now") java.time.LocalDateTime now,
            @Param("until") java.time.LocalDateTime until);

    @Update(
            "UPDATE job_lease SET lease_until=#{until} WHERE name='pay-compensation' AND deleted=0"
                + " AND owner=#{owner} AND lease_until > #{now}")
    int renew(
            @Param("owner") String owner,
            @Param("now") java.time.LocalDateTime now,
            @Param("until") java.time.LocalDateTime until);

    @Update(
            "UPDATE job_lease SET lease_until=#{now} WHERE name='pay-compensation' AND deleted=0"
                + " AND owner=#{owner}")
    int release(@Param("owner") String owner, @Param("now") java.time.LocalDateTime now);
}
